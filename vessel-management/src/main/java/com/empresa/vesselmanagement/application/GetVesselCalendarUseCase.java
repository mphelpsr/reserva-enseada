package com.empresa.vesselmanagement.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.empresa.vesselmanagement.application.exception.VesselNotFoundException;
import com.empresa.vesselmanagement.domain.availability.DeclaredAvailability;
import com.empresa.vesselmanagement.domain.availability.RotationSchedule;
import com.empresa.vesselmanagement.domain.availability.TourType;
import com.empresa.vesselmanagement.infrastructure.dynamodb.AvailabilityRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.RotationScheduleRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.VesselRepository;

/**
 * T044. Leitura consolidada para o painel desktop. `alto_mar.disponivel` já
 * reflete o efeito do rodízio (FR-013/FR-014) — nunca `true` num dia bloqueado,
 * mesmo que a marcação de disponibilidade em si já exista (o gate transacional de
 * T041/T042 evita essa combinação, mas o cálculo aqui é defensivo mesmo assim).
 */
@Service
public class GetVesselCalendarUseCase {

    private final VesselRepository vesselRepository;
    private final AvailabilityRepository availabilityRepository;
    private final RotationScheduleRepository rotationScheduleRepository;

    public GetVesselCalendarUseCase(
            VesselRepository vesselRepository,
            AvailabilityRepository availabilityRepository,
            RotationScheduleRepository rotationScheduleRepository) {
        this.vesselRepository = vesselRepository;
        this.availabilityRepository = availabilityRepository;
        this.rotationScheduleRepository = rotationScheduleRepository;
    }

    public VesselCalendar getCalendar(String vesselId, LocalDate from, LocalDate to) {
        vesselRepository.findById(vesselId).orElseThrow(() -> new VesselNotFoundException(vesselId));

        List<DeclaredAvailability> availabilities = availabilityRepository.findByVesselAndDateRange(vesselId, from, to);

        List<VesselCalendar.CalendarDay> dias = new ArrayList<>();
        for (LocalDate data = from; !data.isAfter(to); data = data.plusDays(1)) {
            Optional<DeclaredAvailability> altoMar = findFor(availabilities, data, TourType.ALTO_MAR);
            Optional<DeclaredAvailability> orla = findFor(availabilities, data, TourType.ORLA);
            boolean rotationBloqueado = rotationScheduleRepository.findByVesselDate(vesselId, data)
                    .map(RotationSchedule::isBloqueado)
                    .orElse(false);

            boolean altoMarDisponivel = altoMar.map(DeclaredAvailability::isDisponivel).orElse(false) && !rotationBloqueado;

            dias.add(new VesselCalendar.CalendarDay(
                    data,
                    new VesselCalendar.TourAvailability(altoMarDisponivel, altoMar.map(DeclaredAvailability::getMotivo).orElse(null)),
                    new VesselCalendar.TourAvailability(
                            orla.map(DeclaredAvailability::isDisponivel).orElse(false),
                            orla.map(DeclaredAvailability::getMotivo).orElse(null))));
        }

        return new VesselCalendar(vesselId, dias);
    }

    private Optional<DeclaredAvailability> findFor(List<DeclaredAvailability> availabilities, LocalDate data, TourType tipoPasseio) {
        return availabilities.stream()
                .filter(a -> a.getData().equals(data) && a.getTipoPasseio() == tipoPasseio)
                .findFirst();
    }
}
