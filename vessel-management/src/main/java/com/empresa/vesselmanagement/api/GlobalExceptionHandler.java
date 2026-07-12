package com.empresa.vesselmanagement.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.empresa.vesselmanagement.application.exception.AdvisoryNotFoundException;
import com.empresa.vesselmanagement.application.exception.IncompatibleTargetVesselException;
import com.empresa.vesselmanagement.application.exception.InvalidVesselDataException;
import com.empresa.vesselmanagement.application.exception.PaymentRecebedorIdMissingException;
import com.empresa.vesselmanagement.application.exception.RotationAvailabilityConflictException;
import com.empresa.vesselmanagement.application.exception.SeatLimitExceedsCapacityException;
import com.empresa.vesselmanagement.application.exception.VesselHasFutureBookingsException;
import com.empresa.vesselmanagement.application.exception.VesselNotFoundException;
import com.empresa.vesselmanagement.infrastructure.dynamodb.DuplicateVesselIdentifierException;

/** Traduz as exceções de negócio da camada de aplicação para o contrato HTTP (Fase 3.2). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({VesselNotFoundException.class, AdvisoryNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler({InvalidVesselDataException.class, SeatLimitExceedsCapacityException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(DuplicateVesselIdentifierException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateVesselIdentifierException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("DUPLICATE_VESSEL_IDENTIFIER", e.getMessage()));
    }

    @ExceptionHandler({PaymentRecebedorIdMissingException.class, IncompatibleTargetVesselException.class})
    public ResponseEntity<ErrorResponse> handleUnprocessable(RuntimeException e) {
        String code = e instanceof PaymentRecebedorIdMissingException
                ? "PAYMENT_RECEBEDOR_ID_MISSING"
                : "INCOMPATIBLE_TARGET_VESSEL";
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponse(code, e.getMessage()));
    }

    @ExceptionHandler(VesselHasFutureBookingsException.class)
    public ResponseEntity<VesselHasFutureBookingsResponse> handleFutureBookings(VesselHasFutureBookingsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new VesselHasFutureBookingsResponse("VESSEL_HAS_FUTURE_BOOKINGS", e.getMessage(), true));
    }

    @ExceptionHandler(RotationAvailabilityConflictException.class)
    public ResponseEntity<RotationConflictResponse> handleRotationConflict(RotationAvailabilityConflictException e) {
        List<RotationConflictOption> options = List.of(
                new RotationConflictOption("ALTERAR_DISPONIBILIDADE_ALTO_MAR",
                        "Mudar o dia da disponibilidade de Alto Mar para outra data"),
                new RotationConflictOption("ALTERAR_OU_REMOVER_RODIZIO",
                        "Alterar ou remover o rodízio cadastrado para esse dia"));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new RotationConflictResponse("ROTATION_AVAILABILITY_CONFLICT", e.getMessage(), options));
    }
}
