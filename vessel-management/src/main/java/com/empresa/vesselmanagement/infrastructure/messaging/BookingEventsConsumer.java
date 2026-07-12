package com.empresa.vesselmanagement.infrastructure.messaging;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.empresa.vesselmanagement.application.event.BookingConfirmedEventPayload;
import com.empresa.vesselmanagement.domain.availability.TourType;
import com.empresa.vesselmanagement.infrastructure.dynamodb.BookingCountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * T059b — consumidor SQS do tópico `booking.confirmed` (fila `booking_events`,
 * infra/sqs.tf T006b). Mantém a réplica local `ConfirmedBookingCount` (T031b),
 * decisão de 2026-07-12 registrada em plan.md — "Eventos Consumidos".
 *
 * A mesma fila/Lambda também recebe `booking.transferred`/`booking.cancelled`
 * (T059, ainda pendente — depende do payload combinado com `tasks-booking.md`
 * T055). Esta classe já está preparada para os três, roteando pelo atributo de
 * mensagem `event-type` (mesma convenção usada por SnsEventListener) — só falta
 * implementar os outros dois `case` quando T059 for desbloqueada.
 *
 * SQS assinado a um tópico SNS recebe o envelope da notificação (não o payload
 * puro) — o corpo de cada registro é o JSON de notificação do SNS, com o
 * payload de negócio dentro do campo "Message" (string).
 */
@Component("bookingEventsConsumerService")
public class BookingEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingEventsConsumer.class);

    private final BookingCountRepository bookingCountRepository;
    private final ObjectMapper objectMapper;

    public BookingEventsConsumer(BookingCountRepository bookingCountRepository, ObjectMapper objectMapper) {
        this.bookingCountRepository = bookingCountRepository;
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
            case "booking.transferred", "booking.cancelled" ->
                    log.info("{} recebido — consumidor pleno (T059) ainda não implementado, aguardando contrato de tasks-booking.md T055", eventType);
            default -> log.warn("event-type desconhecido em booking_events: {}", eventType);
        }
    }

    private void handleBookingConfirmed(String messageJson) throws Exception {
        BookingConfirmedEventPayload payload = objectMapper.readValue(messageJson, BookingConfirmedEventPayload.class);
        bookingCountRepository.increment(payload.vesselId(), LocalDate.parse(payload.data()), TourType.fromValue(payload.tipoPasseio()));
    }
}
