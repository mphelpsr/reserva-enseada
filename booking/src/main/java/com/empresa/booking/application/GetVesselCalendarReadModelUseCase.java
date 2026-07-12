package com.empresa.booking.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.empresa.booking.application.exception.VesselNotFoundException;
import com.empresa.booking.domain.availability.TourType;
import com.empresa.booking.domain.seathold.SeatCount;
import com.empresa.booking.infrastructure.dynamodb.SeatCountRepository;

/**
 * T045. FR-001/FR-002: lê a réplica local de disponibilidade/vagas restantes
 * (`SeatCount`, mantida via `vessel.availability.changed`/
 * `vessel.seatlimit.changed` — T049/T050) — nunca calcula nada por conta
 * própria, só reflete o que o vessel-management já decidiu.
 *
 * Sem um evento dedicado a "embarcação registrada", `SeatCountRepository.existsForVessel`
 * (T049/T050 já terem escrito PELO MENOS um SeatCount para este vesselId) é o
 * proxy usado para "a embarcação existe" — distingue embarcação nunca
 * anunciada (404) de embarcação conhecida sem dado para o intervalo pedido
 * (200, indisponível/zero vagas nesse trecho).
 */
@Service
public class GetVesselCalendarReadModelUseCase {

    private final SeatCountRepository seatCountRepository;

    public GetVesselCalendarReadModelUseCase(SeatCountRepository seatCountRepository) {
        this.seatCountRepository = seatCountRepository;
    }

    public VesselCalendar getCalendar(String vesselId, LocalDate from, LocalDate to) {
        if (!seatCountRepository.existsForVessel(vesselId)) {
            throw new VesselNotFoundException(vesselId);
        }

        List<VesselCalendar.Dia> dias = from.datesUntil(to.plusDays(1))
                .map(data -> new VesselCalendar.Dia(
                        data,
                        diaTipoPasseio(vesselId, data, TourType.ALTO_MAR),
                        diaTipoPasseio(vesselId, data, TourType.ORLA)))
                .toList();

        return new VesselCalendar(vesselId, dias);
    }

    private VesselCalendar.DiaTipoPasseio diaTipoPasseio(String vesselId, LocalDate data, TourType tipoPasseio) {
        Optional<SeatCount> seatCount = seatCountRepository.findByVesselDateType(vesselId, data, tipoPasseio);
        return seatCount
                .map(sc -> new VesselCalendar.DiaTipoPasseio(sc.isDisponivel(), sc.vagasRestantes(), sc.getMotivo()))
                .orElseGet(() -> new VesselCalendar.DiaTipoPasseio(false, 0, null));
    }
}
