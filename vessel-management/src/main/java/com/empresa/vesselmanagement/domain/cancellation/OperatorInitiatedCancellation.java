package com.empresa.vesselmanagement.domain.cancellation;

import java.time.LocalDate;

import com.empresa.vesselmanagement.domain.availability.TourType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload do evento `vessel.cancellation.operator-initiated` (FR-007, Princípio VII).
 * Não é persistido como item próprio — plan.md modela só `TRANSFER#{id}`
 * (`BookingTransferAttempt`) como registro da Saga; este objeto é construído a
 * partir de um `BookingTransferAttempt` com status CANCELLED_NO_ALTERNATIVE no
 * momento de publicar o evento (T054), carregando o motivo estruturado e real que
 * o módulo booking DEVE comunicar ao comprador — nunca uma mensagem genérica.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperatorInitiatedCancellation {

    private String vesselId;
    private LocalDate data;
    private TourType tipoPasseio;
    private String motivo;
}
