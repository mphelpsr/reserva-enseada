package com.empresa.vesselmanagement.infrastructure.messaging;

import java.time.LocalDate;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.empresa.vesselmanagement.application.event.AvailabilityChangedEvent;
import com.empresa.vesselmanagement.application.event.BookingCancelledEventPayload;
import com.empresa.vesselmanagement.application.event.BookingConfirmedEventPayload;
import com.empresa.vesselmanagement.application.event.BookingTransferredEventPayload;
import com.empresa.vesselmanagement.domain.availability.DeclaredAvailability;
import com.empresa.vesselmanagement.domain.availability.TourType;
import com.empresa.vesselmanagement.domain.cancellation.BookingTransferAttempt;
import com.empresa.vesselmanagement.domain.cancellation.TransferAttemptStatus;
import com.empresa.vesselmanagement.infrastructure.dynamodb.AvailabilityRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.BookingCountRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.BookingTransferAttemptRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * T059b/T059 — consumidor SQS dos 3 tópicos do booking (fila `booking_events`,
 * infra/sqs.tf T006b), roteando por atributo de mensagem `event-type`.
 *
 * `booking.confirmed` (T059b): mantém a réplica local `ConfirmedBookingCount`
 * (T031b).
 *
 * `booking.cancelled`/`booking.transferred` (T059) fecham o passo final da
 * Saga coreografada de cancelamento (Princípio VII) iniciada por
 * `CancelDayWithBookingsUseCase` (T046): quando há embarcação alternativa
 * (`VIABLE_PENDING`), T046 delibradamente NÃO aplica `disponivel=false` na
 * embarcação de origem — fica aguardando o desfecho do booking. É aqui que
 * esse `disponivel=false` (com o `motivo` original do proprietário) é
 * finalmente aplicado, tanto se o comprador aceitar a transferência quanto se
 * recusar/a oferta expirar — a decisão do proprietário de tirar aquele dia de
 * operação vale nos dois casos, só o desfecho de cada reserva individual
 * muda. Ver plan.md, "Contrato da Saga", nota sobre esta decisão de
 * implementação (derivada do código de T046, não assumida).
 *
 * SQS assinado a um tópico SNS recebe o envelope da notificação (não o payload
 * puro) — o corpo de cada registro é o JSON de notificação do SNS, com o
 * payload de negócio dentro do campo "Message" (string).
 */
@Component("bookingEventsConsumerService")
public class BookingEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingEventsConsumer.class);

    private final BookingCountRepository bookingCountRepository;
    private final BookingTransferAttemptRepository transferAttemptRepository;
    private final AvailabilityRepository availabilityRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public BookingEventsConsumer(
            BookingCountRepository bookingCountRepository,
            BookingTransferAttemptRepository transferAttemptRepository,
            AvailabilityRepository availabilityRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper) {
        this.bookingCountRepository = bookingCountRepository;
        this.transferAttemptRepository = transferAttemptRepository;
        this.availabilityRepository = availabilityRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    public void handle(SQSEvent event) {
        for (SQSEvent.SQSMessage record : event.getRecords()) {
            try {
                processRecord(record.getBody());
            } catch (Exception e) {
                log.error("Falha ao processar mensagem SQS de booking_events: {}", record.getMessageId(), e);
                throw new RuntimeException(e); // deixa a mensagem voltar pra fila / cair na DLQ
            }
        }
    }

    private void processRecord(String snsEnvelopeJson) throws Exception {
        JsonNode envelope = objectMapper.readTree(snsEnvelopeJson);
        String eventType = envelope.path("MessageAttributes").path("event-type").path("Value").asText(null);
        String message = envelope.path("Message").asText(null);

        if (eventType == null || message == null) {
            log.warn("Mensagem SQS sem event-type/Message reconhecível, ignorada: {}", snsEnvelopeJson);
            return;
        }

        switch (eventType) {
            case "booking.confirmed" -> handleBookingConfirmed(message);
            case "booking.cancelled" -> handleBookingCancelled(message);
            case "booking.transferred" -> handleBookingTransferred(message);
            default -> log.warn("event-type desconhecido em booking_events: {}", eventType);
        }
    }

    private void handleBookingConfirmed(String messageJson) throws Exception {
        BookingConfirmedEventPayload payload = objectMapper.readValue(messageJson, BookingConfirmedEventPayload.class);
        bookingCountRepository.increment(payload.vesselId(), LocalDate.parse(payload.data()), TourType.fromValue(payload.tipoPasseio()));
    }

    private void handleBookingCancelled(String messageJson) throws Exception {
        BookingCancelledEventPayload payload = objectMapper.readValue(messageJson, BookingCancelledEventPayload.class);
        bookingCountRepository.decrement(payload.vesselId(), LocalDate.parse(payload.data()), TourType.fromValue(payload.tipoPasseio()));

        if (payload.transferAttemptId() == null) {
            return; // desistência direta do comprador — sem tentativa de transferência envolvida
        }
        finalizeTransferAttempt(payload.vesselId(), payload.transferAttemptId(), TransferAttemptStatus.CANCELLED_NO_ALTERNATIVE);
    }

    private void handleBookingTransferred(String messageJson) throws Exception {
        BookingTransferredEventPayload payload = objectMapper.readValue(messageJson, BookingTransferredEventPayload.class);
        LocalDate data = LocalDate.parse(payload.data());
        TourType tipoPasseio = TourType.fromValue(payload.tipoPasseio());

        bookingCountRepository.decrement(payload.vesselId(), data, tipoPasseio);
        bookingCountRepository.increment(payload.targetVesselId(), data, tipoPasseio);

        finalizeTransferAttempt(payload.vesselId(), payload.transferAttemptId(), TransferAttemptStatus.TRANSFERRED);
    }

    /**
     * Efetiva o desfecho pendente desde T046: fecha o registro da tentativa e
     * aplica `disponivel=false` (motivo original do proprietário) na
     * embarcação de ORIGEM — independente de aceite ou recusa/expiração,
     * porque a decisão de tirar o dia de operação já era do proprietário
     * desde `CancelDayWithBookingsUseCase`, só ficou pendente aguardando o
     * comprador.
     */
    private void finalizeTransferAttempt(String vesselId, String transferAttemptId, TransferAttemptStatus status) {
        Optional<BookingTransferAttempt> attempt = transferAttemptRepository.findByVesselIdAndId(vesselId, transferAttemptId);
        if (attempt.isEmpty()) {
            log.warn("BookingTransferAttempt {} não encontrado para vessel {} — ignorado", transferAttemptId, vesselId);
            return;
        }

        BookingTransferAttempt found = attempt.get();
        found.setStatus(status);
        transferAttemptRepository.save(found);

        availabilityRepository.save(DeclaredAvailability.builder()
                .vesselId(found.getVesselId())
                .data(found.getData())
                .tipoPasseio(found.getTipoPasseio())
                .disponivel(false)
                .motivo(found.getMotivo())
                .build());
        eventPublisher.publishEvent(
                new AvailabilityChangedEvent(found.getVesselId(), found.getData(), found.getTipoPasseio(), false, found.getMotivo()));
    }
}
