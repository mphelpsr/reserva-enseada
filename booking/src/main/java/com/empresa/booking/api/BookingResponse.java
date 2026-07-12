package com.empresa.booking.api;

import java.time.Instant;
import java.time.LocalDate;

import com.empresa.booking.domain.availability.TourType;
import com.empresa.booking.domain.booking.Booking;
import com.empresa.booking.domain.booking.BookingStatus;

/**
 * Forma HTTP de uma reserva — usada por GET/list/confirm/respond-transfer.
 * Só difere do domínio `Booking` no nome dos três campos financeiros (sem o
 * sufixo `Centavos`, que é detalhe de armazenamento, não de contrato de API).
 */
public record BookingResponse(
        String id,
        String buyerId,
        String vesselId,
        LocalDate data,
        TourType tipoPasseio,
        int quantidade,
        BookingStatus status,
        Long valorPago,
        Long valorComissao,
        Long valorLiquido,
        Instant compradaEm,
        String motivo,
        String targetVesselId,
        String transferAttemptId,
        Instant transferOfferExpiresAt) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getBuyerId(),
                booking.getVesselId(),
                booking.getData(),
                booking.getTipoPasseio(),
                booking.getQuantidade(),
                booking.getStatus(),
                booking.getValorPagoCentavos(),
                booking.getValorComissaoCentavos(),
                booking.getValorLiquidoCentavos(),
                booking.getCompradaEm(),
                booking.getMotivo(),
                booking.getTargetVesselId(),
                booking.getTransferAttemptId(),
                booking.getTransferOfferExpiresAt());
    }
}
