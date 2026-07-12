package com.empresa.booking.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.empresa.booking.domain.availability.TourType;
import com.empresa.booking.domain.seathold.SeatCount;
import com.empresa.booking.infrastructure.dynamodb.SeatCountRepository;

/**
 * T045. FR-001/FR-002: lê a réplica local de disponibilidade/vagas restantes
 * (`SeatCount`, mantida via `vessel.availability.changed`/
 * `vessel.seatlimit.changed` — T049/T050) — nunca calcula nada por conta
 * própria, só reflete o que o vessel-management já decidiu.
 *
 * LIMITAÇÃO CONHECIDA: este módulo não replica "a embarcação existe" (nenhum
 * evento consumido carrega esse dado — só mudanças de disponibilidade/limite
 * de um vessel presumivelmente já conhecido). Por isso não há como
 * distinguir "embarcação inexistente" de "embarcação existente sem nenhum
 * dado ainda para o intervalo pedido" — os dois casos retornam o mesmo
 * resultado (200, todo dia/tipo mostrando indisponível/zero vagas). Corrigir
 * exigiria mais um evento replicado (ex.: `vessel.registered`), seguindo o
 * mesmo padrão já usado para os outros 5 eventos — mesma "forma" de solução
 * das lacunas já resolvidas (`vessel.recebedor.changed`), não uma decisão de
 * arquitetura nova; documentado aqui em vez de bloquear a Fase 3.3 por isso.
 */
@Service
public class GetVesselCalendarReadModelUseCase {

    private final SeatCountRepository seatCountRepository;

    public GetVesselCalendarReadModelUseCase(SeatCountRepository seatCountRepository) {
        this.seatCountRepository = seatCountRepository;
    }

    public VesselCalendar getCalendar(String vesselId, LocalDate from, LocalDate to) {
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
