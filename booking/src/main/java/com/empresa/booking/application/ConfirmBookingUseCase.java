package com.empresa.booking.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.empresa.booking.application.exception.HoldNotFoundException;
import com.empresa.booking.application.exception.PaymentRecebedorNotConfiguredException;
import com.empresa.booking.domain.booking.Booking;
import com.empresa.booking.domain.booking.BookingStatus;
import com.empresa.booking.domain.booking.VesselRecebedor;
import com.empresa.booking.domain.seathold.SeatHold;
import com.empresa.booking.infrastructure.dynamodb.BookingRepository;
import com.empresa.booking.infrastructure.dynamodb.SeatCountRepository;
import com.empresa.booking.infrastructure.dynamodb.SeatHoldRepository;
import com.empresa.booking.infrastructure.dynamodb.VesselRecebedorRepository;
import com.empresa.booking.infrastructure.payment.PagarmeClient;
import com.empresa.booking.infrastructure.payment.PaymentFailedException;
import com.empresa.booking.infrastructure.payment.PagarmeOrderResult;

/**
 * T039. FR-005: confirma a reserva somente após pagamento aprovado — nunca
 * antes. FR-015: split de 12% plataforma / 88% proprietário, percentual
 * configurável (nunca hardcoded).
 */
@Service
public class ConfirmBookingUseCase {

    private final SeatHoldRepository seatHoldRepository;
    private final SeatCountRepository seatCountRepository;
    private final BookingRepository bookingRepository;
    private final VesselRecebedorRepository vesselRecebedorRepository;
    private final PagarmeClient pagarmeClient;
    private final int comissaoPercentual;

    public ConfirmBookingUseCase(
            SeatHoldRepository seatHoldRepository,
            SeatCountRepository seatCountRepository,
            BookingRepository bookingRepository,
            VesselRecebedorRepository vesselRecebedorRepository,
            PagarmeClient pagarmeClient,
            @Value("${app.payment.platform-commission-percentage:12}") int comissaoPercentual) {
        this.seatHoldRepository = seatHoldRepository;
        this.seatCountRepository = seatCountRepository;
        this.bookingRepository = bookingRepository;
        this.vesselRecebedorRepository = vesselRecebedorRepository;
        this.pagarmeClient = pagarmeClient;
        this.comissaoPercentual = comissaoPercentual;
    }

    public Booking confirm(ConfirmBookingCommand command) {
        SeatHold hold = seatHoldRepository.findById(command.holdId())
                .filter(h -> !h.isExpired(Instant.now()))
                .orElseThrow(() -> new HoldNotFoundException(command.holdId()));

        VesselRecebedor recebedor = vesselRecebedorRepository.findByVesselId(hold.getVesselId())
                .orElseThrow(() -> new PaymentRecebedorNotConfiguredException(hold.getVesselId()));

        long valorTotal = hold.getValorTotalCentavos();
        long valorComissao = Math.round(valorTotal * comissaoPercentual / 100.0);
        long valorLiquido = valorTotal - valorComissao;

        PagarmeOrderResult pagamento = pagarmeClient.createOrderWithSplit(
                command.paymentReference(), recebedor.getRecebedorId(), valorTotal, valorComissao, valorLiquido);

        if (!"paid".equals(pagamento.status())) {
            throw new PaymentFailedException("Pagar.me retornou status " + pagamento.status() + " para o pedido " + pagamento.orderId());
        }

        Booking booking = Booking.builder()
                .id(UUID.randomUUID().toString())
                .buyerId(hold.getBuyerId())
                .vesselId(hold.getVesselId())
                .data(hold.getData())
                .tipoPasseio(hold.getTipoPasseio())
                .quantidade(hold.getQuantidade())
                .status(BookingStatus.CONFIRMADA)
                .valorPagoCentavos(valorTotal)
                .valorComissaoCentavos(valorComissao)
                .valorLiquidoCentavos(valorLiquido)
                .compradaEm(Instant.now())
                .build();

        bookingRepository.save(booking);
        seatCountRepository.moveHeldToSold(hold.getVesselId(), hold.getData(), hold.getTipoPasseio(), hold.getQuantidade());
        seatHoldRepository.delete(hold.getId());

        return booking;
    }
}
