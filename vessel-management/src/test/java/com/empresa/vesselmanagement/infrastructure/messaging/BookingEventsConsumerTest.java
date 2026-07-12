package com.empresa.vesselmanagement.infrastructure.messaging;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.empresa.vesselmanagement.domain.availability.TourType;
import com.empresa.vesselmanagement.infrastructure.dynamodb.BookingCountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/** T059b — roteamento por `event-type` e parsing do envelope SNS-em-SQS. */
@ExtendWith(MockitoExtension.class)
class BookingEventsConsumerTest {

    @Mock
    private BookingCountRepository bookingCountRepository;

    @Test
    void deveIncrementarContadorAoReceberBookingConfirmed() {
        BookingEventsConsumer consumer = new BookingEventsConsumer(bookingCountRepository, new ObjectMapper().findAndRegisterModules());

        String envelope = """
                {
                  "Message": "{\\"vesselId\\":\\"vessel-1\\",\\"data\\":\\"2026-12-24\\",\\"tipoPasseio\\":\\"alto_mar\\"}",
                  "MessageAttributes": {
                    "event-type": { "Type": "String", "Value": "booking.confirmed" }
                  }
                }
                """;

        SQSEvent event = new SQSEvent();
        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setMessageId("msg-1");
        message.setBody(envelope);
        event.setRecords(List.of(message));

        consumer.handle(event);

        verify(bookingCountRepository).increment("vessel-1", LocalDate.of(2026, 12, 24), TourType.ALTO_MAR);
    }

    @Test
    void devePularEventoNaoReconhecidoSemErro() {
        BookingEventsConsumer consumer = new BookingEventsConsumer(bookingCountRepository, new ObjectMapper().findAndRegisterModules());

        String envelope = """
                {
                  "Message": "{}",
                  "MessageAttributes": {}
                }
                """;

        SQSEvent event = new SQSEvent();
        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setMessageId("msg-2");
        message.setBody(envelope);
        event.setRecords(List.of(message));

        consumer.handle(event);

        verifyNoInteractions(bookingCountRepository);
    }

    @Test
    void deveIgnorarBookingTransferredPorEnquanto() {
        BookingEventsConsumer consumer = new BookingEventsConsumer(bookingCountRepository, new ObjectMapper().findAndRegisterModules());

        String envelope = """
                {
                  "Message": "{\\"bookingId\\":\\"b-1\\"}",
                  "MessageAttributes": {
                    "event-type": { "Type": "String", "Value": "booking.transferred" }
                  }
                }
                """;

        SQSEvent event = new SQSEvent();
        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setMessageId("msg-3");
        message.setBody(envelope);
        event.setRecords(List.of(message));

        consumer.handle(event);

        verifyNoInteractions(bookingCountRepository);
    }
}
