package com.empresa.booking.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;

import com.empresa.booking.domain.availability.TourType;
import com.empresa.booking.domain.booking.Booking;
import com.empresa.booking.domain.booking.BookingStatus;
import com.empresa.booking.domain.operatorevents.VesselTransferViable;
import com.empresa.booking.infrastructure.dynamodb.BookingRepository;

/**
 * T043. Consumidor de `vessel.transfer.viable` (FR-009, cenário 7): notifica
 * o comprador com as novas condições e aguarda confirmação por até 48h
 * (T041 fecha essa resposta; o auto-cancelamento sem resposta é T046).
 *
 * Só afeta reservas CONFIRMADA — filtro que já implementa sozinho a regra de
 * prioridade do FR-009 (T024): se o comprador já cancelou antes da oferta
 * chegar, a reserva não está mais CONFIRMADA, então esta oferta é
 * naturalmente descartada, sem lógica de corrida explícita.
 */
@Service
public class ProcessTransferOfferUseCase {

    private static final int JANELA_RESPOSTA_HORAS = 48;

    private final BookingRepository bookingRepository;

    public ProcessTransferOfferUseCase(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public void process(VesselTransferViable event) {
        LocalDate data = LocalDate.parse(event.data());
        TourType tipoPasseio = TourType.fromValue(event.tipoPasseio());

        List<Booking> afetadas = bookingRepository.findByVesselDateAndType(event.vesselId(), data, tipoPasseio);

        for (Booking booking : afetadas) {
            if (booking.getStatus() != BookingStatus.CONFIRMADA) {
                continue;
            }

            booking.setStatus(BookingStatus.AGUARDANDO_TRANSFERENCIA);
            booking.setTargetVesselId(event.targetVesselId());
            booking.setTransferAttemptId(event.id());
            booking.setTransferOfferExpiresAt(Instant.now().plus(JANELA_RESPOSTA_HORAS, ChronoUnit.HOURS));
            booking.setMotivo(event.motivo());
            bookingRepository.save(booking);
        }
    }
}
