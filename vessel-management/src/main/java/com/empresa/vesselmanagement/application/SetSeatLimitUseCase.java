package com.empresa.vesselmanagement.application;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.empresa.vesselmanagement.application.exception.SeatLimitExceedsCapacityException;
import com.empresa.vesselmanagement.application.exception.VesselNotFoundException;
import com.empresa.vesselmanagement.domain.availability.TourType;
import com.empresa.vesselmanagement.domain.seatlimit.DefaultSeatUsageCounter;
import com.empresa.vesselmanagement.domain.seatlimit.PlatformSeatLimit;
import com.empresa.vesselmanagement.domain.seatlimit.SeatLimitOrigin;
import com.empresa.vesselmanagement.domain.vessel.Vessel;
import com.empresa.vesselmanagement.infrastructure.dynamodb.SeatLimitRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.VesselRepository;

/**
 * T043. FR-015 — Opção C: reduzir o limite nunca é bloqueado. Ausência de
 * indicação aplica 10% da capacidade máxima (arredondado para baixo, mínimo 1) nas
 * duas primeiras vezes (contador cumulativo, `DefaultSeatUsageCounter`); da 3ª vez
 * em diante, zero vagas até definição explícita — sem incrementar o contador além
 * do limite.
 */
@Service
public class SetSeatLimitUseCase {

    private final VesselRepository vesselRepository;
    private final SeatLimitRepository seatLimitRepository;

    public SetSeatLimitUseCase(VesselRepository vesselRepository, SeatLimitRepository seatLimitRepository) {
        this.vesselRepository = vesselRepository;
        this.seatLimitRepository = seatLimitRepository;
    }

    public SetSeatLimitResult setSeatLimit(String vesselId, LocalDate data, TourType tipoPasseio, Integer limiteExplicito) {
        Vessel vessel = vesselRepository.findById(vesselId).orElseThrow(() -> new VesselNotFoundException(vesselId));

        int limite;
        SeatLimitOrigin origem;
        int vezesPadraoAplicado;

        if (limiteExplicito != null) {
            if (limiteExplicito > vessel.getCapacidadeMaxima()) {
                throw new SeatLimitExceedsCapacityException(limiteExplicito, vessel.getCapacidadeMaxima());
            }
            limite = limiteExplicito;
            origem = SeatLimitOrigin.MANUAL;
            vezesPadraoAplicado = seatLimitRepository.getCounter(vesselId).getVezesAplicado();
        } else {
            DefaultSeatUsageCounter counter = seatLimitRepository.getCounter(vesselId);
            if (counter.podeAplicarPadrao()) {
                limite = Math.max(1, vessel.getCapacidadeMaxima() / 10);
                origem = SeatLimitOrigin.PADRAO_AUTOMATICO;
                vezesPadraoAplicado = seatLimitRepository.incrementCounter(vesselId);
            } else {
                limite = 0;
                origem = SeatLimitOrigin.ZERO_SEM_PADRAO;
                vezesPadraoAplicado = counter.getVezesAplicado();
            }
        }

        PlatformSeatLimit seatLimit = PlatformSeatLimit.builder()
                .vesselId(vesselId)
                .data(data)
                .tipoPasseio(tipoPasseio)
                .limite(limite)
                .origem(origem)
                .build();

        seatLimitRepository.save(seatLimit);

        return new SetSeatLimitResult(seatLimit, vezesPadraoAplicado);
    }
}
