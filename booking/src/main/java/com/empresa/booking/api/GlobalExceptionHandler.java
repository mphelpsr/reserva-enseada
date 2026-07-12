package com.empresa.booking.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.empresa.booking.application.exception.BookingNotFoundException;
import com.empresa.booking.application.exception.CancellationWindowExpiredException;
import com.empresa.booking.application.exception.HoldNotFoundException;
import com.empresa.booking.application.exception.InsufficientSeatsException;
import com.empresa.booking.application.exception.MinimumAdvancePurchaseException;
import com.empresa.booking.application.exception.NoPendingTransferOfferException;
import com.empresa.booking.application.exception.PaymentRecebedorNotConfiguredException;
import com.empresa.booking.application.exception.VesselNotFoundException;
import com.empresa.booking.infrastructure.payment.PaymentFailedException;

/** Traduz as exceções de negócio da camada de aplicação para o contrato HTTP (Fase 3.2). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({BookingNotFoundException.class, HoldNotFoundException.class, VesselNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(MinimumAdvancePurchaseException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(MinimumAdvancePurchaseException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("MINIMUM_ADVANCE_PURCHASE_NOT_MET", e.getMessage()));
    }

    @ExceptionHandler(InsufficientSeatsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientSeats(InsufficientSeatsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("INSUFFICIENT_SEATS", e.getMessage()));
    }

    @ExceptionHandler(CancellationWindowExpiredException.class)
    public ResponseEntity<ErrorResponse> handleCancellationWindowExpired(CancellationWindowExpiredException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("CANCELLATION_WINDOW_EXPIRED", e.getMessage()));
    }

    @ExceptionHandler(NoPendingTransferOfferException.class)
    public ResponseEntity<ErrorResponse> handleNoPendingTransferOffer(NoPendingTransferOfferException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("NO_PENDING_TRANSFER_OFFER", e.getMessage()));
    }

    @ExceptionHandler(PaymentRecebedorNotConfiguredException.class)
    public ResponseEntity<ErrorResponse> handlePaymentRecebedorNotConfigured(PaymentRecebedorNotConfiguredException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("PAYMENT_RECEBEDOR_ID_MISSING", e.getMessage()));
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<ErrorResponse> handlePaymentFailed(PaymentFailedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponse("PAYMENT_FAILED", e.getMessage()));
    }
}
