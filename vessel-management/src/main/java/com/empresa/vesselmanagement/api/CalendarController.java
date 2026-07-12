package com.empresa.vesselmanagement.api;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.empresa.vesselmanagement.application.GetVesselCalendarUseCase;
import com.empresa.vesselmanagement.application.VesselCalendar;

/** T050. Visão consolidada para o painel desktop (plan.md). */
@RestController
@RequestMapping("/vessels/{vesselId}/calendar")
public class CalendarController {

    private final GetVesselCalendarUseCase getVesselCalendarUseCase;

    public CalendarController(GetVesselCalendarUseCase getVesselCalendarUseCase) {
        this.getVesselCalendarUseCase = getVesselCalendarUseCase;
    }

    @GetMapping
    public VesselCalendar getCalendar(
            @PathVariable String vesselId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return getVesselCalendarUseCase.getCalendar(vesselId, from, to);
    }
}
