package com.empresa.booking.infrastructure.messaging;

import java.time.LocalDate;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.empresa.booking.application.ProcessOperatorCancellationUseCase;
import com.empresa.booking.application.ProcessTransferOfferUseCase;
import com.empresa.booking.domain.availability.TourType;
import com.empresa.booking.domain.booking.VesselRecebedor;
import com.empresa.booking.domain.operatorevents.OperatorInitiatedCancellation;
import com.empresa.booking.domain.operatorevents.VesselAvailabilityChanged;
import com.empresa.booking.domain.operatorevents.VesselRecebedorChanged;
import com.empresa.booking.domain.operatorevents.VesselSeatLimitChanged;
import com.empresa.booking.domain.operatorevents.VesselTransferViable;
import com.empresa.booking.infrastructure.dynamodb.SeatCountRepository;
import com.empresa.booking.infrastructure.dynamodb.VesselRecebedorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * T049-T052b. Consumidor SQS da fila `operator_events` (infra/sqs.tf, T005),
 * assinada aos 4 tópicos SNS do vessel-management já provisionados (mais o
 * 5º, `vessel.recebedor.changed`, quando existir do outro lado — ver
 * VesselRecebedorChanged). Bean `operatorEventsConsumer` (nome padrão do
 * Spring para @Component, já usado como SPRING_CLOUD_FUNCTION_DEFINITION em
 * infra/lambda.tf desde a Fase 3.1).
 *
 * SQS assinado a um tópico SNS recebe o envelope da notificação — o payload
 * de negócio vem dentro do campo "Message" (string), o tipo do evento vem no
 * atributo de mensagem "event-type" (mesma convenção de vessel-management
 * BookingEventsConsumer).
 */
@Component
public class OperatorEventsConsumer implements Consumer<SQSEvent> {

    private static final Logger log = LoggerFactory.getLogger(OperatorEventsConsumer.class);

    private final ObjectMapper objectMapper;
    private final SeatCountRepository seatCountRepository;
    private final VesselRecebedorRepository vesselRecebedorRepository;
    private final ProcessOperatorCancellationUseCase processOperatorCancellationUseCase;
    private final ProcessTransferOfferUseCase processTransferOfferUseCase;

    public OperatorEventsConsumer(
            ObjectMapper objectMapper,
            SeatCountRepository seatCountRepository,
            VesselRecebedorRepository vesselRecebedorRepository,
            ProcessOperatorCancellationUseCase processOperatorCancellationUseCase,
            ProcessTransferOfferUseCase processTransferOfferUseCase) {
        this.objectMapper = objectMapper;
        this.seatCountRepository = seatCountRepository;
        this.vesselRecebedorRepository = vesselRecebedorRepository;
        this.processOperatorCancellationUseCase = processOperatorCancellationUseCase;
        this.processTransferOfferUseCase = processTransferOfferUseCase;
    }

    @Override
    public void accept(SQSEvent event) {
        for (SQSEvent.SQSMessage record : event.getRecords()) {
            try {
                processRecord(record.getBody());
            } catch (Exception e) {
                log.error("Falha ao processar mensagem SQS de operator_events: {}", record.getMessageId(), e);
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
            case "vessel.availability.changed" -> handleAvailabilityChanged(message);
            case "vessel.seatlimit.changed" -> handleSeatLimitChanged(message);
            case "vessel.cancellation.operator-initiated" -> handleOperatorCancellation(message);
            case "vessel.transfer.viable" -> handleTransferViable(message);
            case "vessel.recebedor.changed" -> handleRecebedorChanged(message);
            default -> log.warn("event-type desconhecido em operator_events: {}", eventType);
        }
    }

    private void handleAvailabilityChanged(String messageJson) throws Exception {
        VesselAvailabilityChanged payload = objectMapper.readValue(messageJson, VesselAvailabilityChanged.class);
        seatCountRepository.upsertDisponibilidade(
                payload.vesselId(),
                LocalDate.parse(payload.data()),
                TourType.fromValue(payload.tipoPasseio()),
                payload.disponivel(),
                payload.motivo());
    }

    private void handleSeatLimitChanged(String messageJson) throws Exception {
        VesselSeatLimitChanged payload = objectMapper.readValue(messageJson, VesselSeatLimitChanged.class);
        seatCountRepository.upsertLimite(
                payload.vesselId(), LocalDate.parse(payload.data()), TourType.fromValue(payload.tipoPasseio()), payload.limite());
    }

    private void handleOperatorCancellation(String messageJson) throws Exception {
        OperatorInitiatedCancellation payload = objectMapper.readValue(messageJson, OperatorInitiatedCancellation.class);
        processOperatorCancellationUseCase.process(payload);
    }

    private void handleTransferViable(String messageJson) throws Exception {
        VesselTransferViable payload = objectMapper.readValue(messageJson, VesselTransferViable.class);
        processTransferOfferUseCase.process(payload);
    }

    private void handleRecebedorChanged(String messageJson) throws Exception {
        VesselRecebedorChanged payload = objectMapper.readValue(messageJson, VesselRecebedorChanged.class);
        vesselRecebedorRepository.save(
                VesselRecebedor.builder().vesselId(payload.vesselId()).pixKey(payload.pixKey()).build());
    }
}
