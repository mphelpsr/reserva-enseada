package com.empresa.booking.domain.cancellation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * FR-006/FR-007: modelo binário de cancelamento por desistência do
 * comprador — direito de arrependimento (Art. 49 CDC) dentro da janela,
 * recusa fora dela, sem escalonamento. Distinta do fluxo de cancelamento por
 * iniciativa do proprietário/operação (Princípio VII, ver
 * ProcessOperatorCancellationUseCase).
 *
 * As DUAS condições precisam valer ao mesmo tempo: até 7 dias corridos da
 * compra E até 48h antes do passeio. Se a compra ocorrer a menos de 7 dias
 * do passeio, a segunda condição já comprime o prazo efetivo sozinha — não
 * há necessidade de um cálculo de "prazo comprimido" separado, as duas
 * checagens combinadas já produzem esse efeito.
 */
public final class CancellationPolicy {

    private static final int JANELA_ARREPENDIMENTO_DIAS = 7;
    private static final int ANTECEDENCIA_MINIMA_HORAS = 48;

    private CancellationPolicy() {
    }

    public static boolean dentroDaJanela(Instant compradaEm, LocalDate dataPasseio, Instant agora) {
        Instant limiteArrependimento = compradaEm.plus(JANELA_ARREPENDIMENTO_DIAS, ChronoUnit.DAYS);
        Instant limiteAntesDoPasseio = dataPasseio.atStartOfDay(ZoneOffset.UTC).toInstant()
                .minus(ANTECEDENCIA_MINIMA_HORAS, ChronoUnit.HOURS);

        return !agora.isAfter(limiteArrependimento) && !agora.isAfter(limiteAntesDoPasseio);
    }
}
