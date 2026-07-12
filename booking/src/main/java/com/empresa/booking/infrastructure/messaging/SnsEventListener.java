package com.empresa.booking.infrastructure.messaging;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.empresa.booking.application.event.BookingCancelledEvent;
import com.empresa.booking.application.event.BookingConfirmedEvent;
import com.empresa.booking.application.event.BookingTransferredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;

/**
 * T053-T055. Publica os eventos de domínio (FR-005/FR-006/FR-007/FR-008/FR-009,
 * Princípio VII) nos tópicos SNS provisionados em infra/sns.tf (T006). Os
 * casos de uso só publicam um `ApplicationEventPublisher` (Princípio IV) —
 * este listener traduz para SNS, mesmo padrão de vessel-management
 * SnsEventListener.
 *
 * Se o ARN do tópico não estiver configurado (dev/teste local sem infra), a
 * publicação é pulada silenciosamente.
 */
@Component
public class SnsEventListener {

    private static final Logger log = LoggerFactory.getLogger(SnsEventListener.class);

    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;
    private final String bookingConfirmedTopicArn;
    private final String bookingCancelledTopicArn;
    private final String bookingTransferredTopicArn;

    public SnsEventListener(
            SnsClient snsClient,
            ObjectMapper objectMapper,
            @Value("${app.sns.booking-confirmed-topic-arn:}") String bookingConfirmedTopicArn,
            @Value("${app.sns.booking-cancelled-topic-arn:}") String bookingCancelledTopicArn,
            @Value("${app.sns.booking-transferred-topic-arn:}") String bookingTransferredTopicArn) {
        this.snsClient = snsClient;
        this.objectMapper = objectMapper;
        this.bookingConfirmedTopicArn = bookingConfirmedTopicArn;
        this.bookingCancelledTopicArn = bookingCancelledTopicArn;
        this.bookingTransferredTopicArn = bookingTransferredTopicArn;
    }

    @EventListener
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        publish(bookingConfirmedTopicArn, "booking.confirmed", event);
    }

    @EventListener
    public void onBookingCancelled(BookingCancelledEvent event) {
        publish(bookingCancelledTopicArn, "booking.cancelled", event);
    }

    @EventListener
    public void onBookingTransferred(BookingTransferredEvent event) {
        publish(bookingTransferredTopicArn, "booking.transferred", event);
    }

    private void publish(String topicArn, String eventType, Object payload) {
        if (topicArn == null || topicArn.isBlank()) {
            log.debug("Tópico SNS não configurado para {} — publicação pulada (payload={})", eventType, payload);
            return;
        }
        try {
            String message = objectMapper.writeValueAsString(payload);
            snsClient.publish(builder -> builder
                    .topicArn(topicArn)
                    .message(message)
                    .messageAttributes(Map.of(
                            "event-type", MessageAttributeValue.builder().dataType("String").stringValue(eventType).build())));
        } catch (Exception e) {
            log.error("Falha ao publicar evento {} no tópico {}", eventType, topicArn, e);
        }
    }
}
