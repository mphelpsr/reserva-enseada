package com.empresa.vesselmanagement.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.empresa.vesselmanagement.application.event.AvailabilityChangedEvent;
import com.empresa.vesselmanagement.domain.availability.DeclaredAvailability;
import com.empresa.vesselmanagement.domain.availability.TourType;
import com.empresa.vesselmanagement.domain.cancellation.BookingTransferAttempt;
import com.empresa.vesselmanagement.domain.cancellation.TransferAttemptStatus;
import com.empresa.vesselmanagement.infrastructure.dynamodb.AvailabilityRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.BookingCountRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.BookingTransferAttemptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/** T059b/T059 — roteamento por `event-type` e parsing do envelope SNS-em-SQS. */
@ExtendWith(MockitoExtension.class)
class BookingEventsConsumerTest {

    @Mock
    private BookingCountRepository bookingCountRepository;

    @Mock
    private BookingTransferAttemptRepository transferAttemptRepository;

    @Mock
    private AvailabilityRepository availabilityRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private BookingEventsConsumer consumer() {
        return new BookingEventsConsumer(
                bookingCountRepository, transferAttemptRepository, availabilityRepository, eventPublisher,
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void deveIncrementarContadorAoReceberBookingConfirmed() {
        SQSEvent event = sqsEventFor("booking.confirmed", "{\"vesselId\":\"vessel-1\",\"data\":\"2026-12-24\",\"tipoPasseio\":\"alto_mar\"}");

        consumer().handle(event);

        verify(bookingCountRepository).increment("vessel-1", LocalDate.of(2026, 12, 24), TourType.ALTO_MAR);
    }

    @Test
    void devePularEventoNaoReconhecidoSemErro() {
        SQSEvent event = sqsEventFor(null, "{}");

        consumer().handle(event);

        verifyNoInteractions(bookingCountRepository, transferAttemptRepository, availabilityRepository, eventPublisher);
    }

    @Test
    void deveApenasDecrementarContadorEmDesistenciaDiretaDoComprador() {
        SQSEvent event = sqsEventFor("booking.cancelled",
                "{\"vesselId\":\"vessel-1\",\"data\":\"2026-12-24\",\"tipoPasseio\":\"alto_mar\",\"bookingId\":\"b-1\",\"transferAttemptId\":null}");

        consumer().handle(event);

        verify(bookingCountRepository).decrement("vessel-1", LocalDate.of(2026, 12, 24), TourType.ALTO_MAR);
        verifyNoInteractions(transferAttemptRepository, availabilityRepository, eventPublisher);
    }

    @Test
    void deveFinalizarDisponibilidadeDaOrigemQuandoTransferenciaERecusadaOuExpira() {
        BookingTransferAttempt attempt = BookingTransferAttempt.builder()
                .id("transfer-1").vesselId("vessel-1").data(LocalDate.of(2026, 12, 24)).tipoPasseio(TourType.ALTO_MAR)
                .motivo("avaria no motor").targetVesselId("vessel-2").status(TransferAttemptStatus.VIABLE_PENDING)
                .build();
        when(transferAttemptRepository.findByVesselIdAndId("vessel-1", "transfer-1")).thenReturn(Optional.of(attempt));

        SQSEvent event = sqsEventFor("booking.cancelled",
                "{\"vesselId\":\"vessel-1\",\"data\":\"2026-12-24\",\"tipoPasseio\":\"alto_mar\",\"bookingId\":\"b-1\","
                        + "\"transferAttemptId\":\"transfer-1\"}");

        consumer().handle(event);

        verify(bookingCountRepository).decrement("vessel-1", LocalDate.of(2026, 12, 24), TourType.ALTO_MAR);
        assertThat(attempt.getStatus()).isEqualTo(TransferAttemptStatus.CANCELLED_NO_ALTERNATIVE);
        verify(transferAttemptRepository).save(attempt);

        ArgumentCaptor<DeclaredAvailability> availabilityCaptor = ArgumentCaptor.forClass(DeclaredAvailability.class);
        verify(availabilityRepository).save(availabilityCaptor.capture());
        assertThat(availabilityCaptor.getValue().isDisponivel()).isFalse();
        assertThat(availabilityCaptor.getValue().getMotivo()).isEqualTo("avaria no motor");
        assertThat(availabilityCaptor.getValue().getVesselId()).isEqualTo("vessel-1");

        verify(eventPublisher).publishEvent(new AvailabilityChangedEvent(
                "vessel-1", LocalDate.of(2026, 12, 24), TourType.ALTO_MAR, false, "avaria no motor"));
    }

    @Test
    void deveMoverContadorEFinalizarDisponibilidadeDaOrigemQuandoTransferenciaEAceita() {
        BookingTransferAttempt attempt = BookingTransferAttempt.builder()
                .id("transfer-1").vesselId("vessel-1").data(LocalDate.of(2026, 12, 24)).tipoPasseio(TourType.ALTO_MAR)
                .motivo("avaria no motor").targetVesselId("vessel-2").status(TransferAttemptStatus.VIABLE_PENDING)
                .build();
        when(transferAttemptRepository.findByVesselIdAndId("vessel-1", "transfer-1")).thenReturn(Optional.of(attempt));

        SQSEvent event = sqsEventFor("booking.transferred",
                "{\"vesselId\":\"vessel-1\",\"data\":\"2026-12-24\",\"tipoPasseio\":\"alto_mar\",\"bookingId\":\"b-1\","
                        + "\"targetVesselId\":\"vessel-2\",\"transferAttemptId\":\"transfer-1\"}");

        consumer().handle(event);

        verify(bookingCountRepository).decrement("vessel-1", LocalDate.of(2026, 12, 24), TourType.ALTO_MAR);
        verify(bookingCountRepository).increment("vessel-2", LocalDate.of(2026, 12, 24), TourType.ALTO_MAR);
        assertThat(attempt.getStatus()).isEqualTo(TransferAttemptStatus.TRANSFERRED);

        ArgumentCaptor<DeclaredAvailability> availabilityCaptor = ArgumentCaptor.forClass(DeclaredAvailability.class);
        verify(availabilityRepository).save(availabilityCaptor.capture());
        assertThat(availabilityCaptor.getValue().getVesselId()).isEqualTo("vessel-1");
        assertThat(availabilityCaptor.getValue().isDisponivel()).isFalse();
    }

    @Test
    void deveIgnorarTentativaDeTransferenciaInexistenteSemFalhar() {
        when(transferAttemptRepository.findByVesselIdAndId("vessel-1", "transfer-fantasma")).thenReturn(Optional.empty());

        SQSEvent event = sqsEventFor("booking.cancelled",
                "{\"vesselId\":\"vessel-1\",\"data\":\"2026-12-24\",\"tipoPasseio\":\"alto_mar\",\"bookingId\":\"b-1\","
                        + "\"transferAttemptId\":\"transfer-fantasma\"}");

        consumer().handle(event);

        verify(bookingCountRepository).decrement("vessel-1", LocalDate.of(2026, 12, 24), TourType.ALTO_MAR);
        verify(transferAttemptRepository, never()).save(any());
        verifyNoInteractions(availabilityRepository, eventPublisher);
    }

    private SQSEvent sqsEventFor(String eventType, String messagePayloadJson) {
        String escapedMessage = messagePayloadJson.replace("\"", "\\\"");
        String messageAttributes = eventType == null
                ? "{}"
                : "{ \"event-type\": { \"Type\": \"String\", \"Value\": \"" + eventType + "\" } }";
        String envelope = """
                {
                  "Message": "%s",
                  "MessageAttributes": %s
                }
                """.formatted(escapedMessage, messageAttributes);

        SQSEvent event = new SQSEvent();
        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setMessageId("msg-" + System.nanoTime());
        message.setBody(envelope);
        event.setRecords(List.of(message));
        return event;
    }
}
