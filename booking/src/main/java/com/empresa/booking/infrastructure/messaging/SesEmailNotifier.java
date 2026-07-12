package com.empresa.booking.infrastructure.messaging;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.empresa.booking.domain.booking.Booking;
import com.empresa.booking.domain.booking.Buyer;
import com.empresa.booking.infrastructure.dynamodb.BuyerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Destination;

/**
 * T057. FR-010: e-mail transacional best-effort (nunca falha o fluxo
 * principal — mesma resiliência de PagarmeClient/SnsEventListener) nos três
 * momentos que a spec exige notificação (confirmada/cancelada/transferida).
 * Templates provisionados em infra/ses.tf.
 *
 * LIMITAÇÃO CONHECIDA: `{{vesselName}}`/`{{targetVesselName}}` recebem o
 * `vesselId` bruto — este módulo não replica o nome legível da embarcação
 * (só o vessel-management tem isso, e nenhum evento consumido carrega esse
 * campo). `{{buyerName}}` só é preenchido de verdade quando `BuyerRepository`
 * encontra um perfil — que hoje nunca existe (ver nota em BuyerRepository).
 * Até um desses dados existir, o envio é pulado silenciosamente (source-email
 * vazio já cobre o caso mais comum, dev/teste local).
 */
@Component
public class SesEmailNotifier {

    private static final Logger log = LoggerFactory.getLogger(SesEmailNotifier.class);

    private final SesClient sesClient;
    private final BuyerRepository buyerRepository;
    private final ObjectMapper objectMapper;
    private final String sourceEmail;
    private final String confirmedTemplate;
    private final String cancelledTemplate;
    private final String transferredTemplate;

    public SesEmailNotifier(
            SesClient sesClient,
            BuyerRepository buyerRepository,
            ObjectMapper objectMapper,
            @Value("${app.ses.source-email:}") String sourceEmail,
            @Value("${app.ses.booking-confirmed-template:}") String confirmedTemplate,
            @Value("${app.ses.booking-cancelled-template:}") String cancelledTemplate,
            @Value("${app.ses.booking-transferred-template:}") String transferredTemplate) {
        this.sesClient = sesClient;
        this.buyerRepository = buyerRepository;
        this.objectMapper = objectMapper;
        this.sourceEmail = sourceEmail;
        this.confirmedTemplate = confirmedTemplate;
        this.cancelledTemplate = cancelledTemplate;
        this.transferredTemplate = transferredTemplate;
    }

    public void notifyConfirmed(Booking booking) {
        send(confirmedTemplate, booking.getBuyerId(), Map.of(
                "vesselName", booking.getVesselId(),
                "data", String.valueOf(booking.getData()),
                "tipoPasseio", booking.getTipoPasseio().getValue()));
    }

    public void notifyCancelled(Booking booking, String motivo) {
        send(cancelledTemplate, booking.getBuyerId(), Map.of(
                "vesselName", booking.getVesselId(),
                "data", String.valueOf(booking.getData()),
                "motivo", motivo == null ? "" : motivo));
    }

    public void notifyTransferred(Booking booking, String targetVesselId) {
        send(transferredTemplate, booking.getBuyerId(), Map.of(
                "data", String.valueOf(booking.getData()),
                "targetVesselName", targetVesselId));
    }

    private void send(String template, String buyerId, Map<String, String> templateDataBase) {
        if (template == null || template.isBlank() || sourceEmail == null || sourceEmail.isBlank()) {
            log.debug("SES não configurado — envio pulado (template={}, buyerId={})", template, buyerId);
            return;
        }

        buyerRepository.findById(buyerId).ifPresentOrElse(
                buyer -> sendTemplated(template, buyer, templateDataBase),
                () -> log.debug("Buyer {} sem perfil replicado — envio de e-mail pulado", buyerId));
    }

    private void sendTemplated(String template, Buyer buyer, Map<String, String> templateDataBase) {
        if (buyer.getEmail() == null || buyer.getEmail().isBlank()) {
            log.debug("Buyer {} sem e-mail cadastrado — envio pulado", buyer.getId());
            return;
        }
        try {
            Map<String, String> templateData = new HashMap<>(templateDataBase);
            templateData.put("buyerName", buyer.getNome() == null ? buyer.getEmail() : buyer.getNome());
            String templateDataJson = objectMapper.writeValueAsString(templateData);

            sesClient.sendTemplatedEmail(builder -> builder
                    .source(sourceEmail)
                    .destination(Destination.builder().toAddresses(buyer.getEmail()).build())
                    .template(template)
                    .templateData(templateDataJson));
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail (template={}, buyerId={})", template, buyer.getId(), e);
        }
    }
}
