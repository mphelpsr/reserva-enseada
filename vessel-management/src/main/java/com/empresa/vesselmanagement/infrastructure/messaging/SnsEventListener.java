package com.empresa.vesselmanagement.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.empresa.vesselmanagement.application.event.AvailabilityChangedEvent;
import com.empresa.vesselmanagement.application.event.CancellationInitiatedEvent;
import com.empresa.vesselmanagement.application.event.SeatLimitChangedEvent;
import com.empresa.vesselmanagement.application.event.TransferViableEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.sns.SnsClient;

/**
 * T052-T055. Publica os eventos de domínio (FR-005, FR-015, FR-007/Princípio VII)
 * nos tópicos SNS provisionados em infra/sns.tf (T005). Desacopla os casos de uso
 * do AWS SDK (Princípio IV): eles só publicam um `ApplicationEventPublisher`, este
 * listener traduz para SNS.
 *
 * Se o ARN do tópico não estiver configurado (dev/teste local sem infra), a
 * publicação é pulada silenciosamente — mantém o app utilizável fora da AWS sem
 * exigir SNS local.
 */
@Component
public class SnsEventListener {

    private static final Logger log = LoggerFactory.getLogger(SnsEventListener.class);

    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;
    private final String availabilityChangedTopicArn;
    private final String seatLimitChangedTopicArn;
    private final String cancellationOperatorInitiatedTopicArn;
    private final String transferViableTopicArn;

    public SnsEventListener(
            SnsClient snsClient,
            ObjectMapper objectMapper,
            @Value("${app.sns.vessel-availability-changed-topic-arn:}") String availabilityChangedTopicArn,
            @Value("${app.sns.vessel-seatlimit-changed-topic-arn:}") String seatLimitChangedTopicArn,
            @Value("${app.sns.vessel-cancellation-operator-initiated-topic-arn:}") String cancellationOperatorInitiatedTopicArn,
            @Value("${app.sns.vessel-transfer-viable-topic-arn:}") String transferViableTopicArn) {
        this.snsClient = snsClient;
        this.objectMapper = objectMapper;
        this.availabilityChangedTopicArn = availabilityChangedTopicArn;
        this.seatLimitChangedTopicArn = seatLimitChangedTopicArn;
        this.cancellationOperatorInitiatedTopicArn = cancellationOperatorInitiatedTopicArn;
        this.transferViableTopicArn = transferViableTopicArn;
    }

    @EventListener
    public void onAvailabilityChanged(AvailabilityChangedEvent event) {
        publish(availabilityChangedTopicArn, "vessel.availability.changed", event);
    }

    @EventListener
    public void onSeatLimitChanged(SeatLimitChangedEvent event) {
        publish(seatLimitChangedTopicArn, "vessel.seatlimit.changed", event);
    }

    @EventListener
    public void onCancellationInitiated(CancellationInitiatedEvent event) {
        publish(cancellationOperatorInitiatedTopicArn, "vessel.cancellation.operator-initiated", event);
    }

    @EventListener
    public void onTransferViable(TransferViableEvent event) {
        publish(transferViableTopicArn, "vessel.transfer.viable", event);
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
                    .messageAttributes(java.util.Map.of(
                            "event-type", software.amazon.awssdk.services.sns.model.MessageAttributeValue.builder()
                                    .dataType("String")
                                    .stringValue(eventType)
                                    .build())));
        } catch (Exception e) {
            log.error("Falha ao publicar evento {} no tópico {}", eventType, topicArn, e);
        }
    }
}
