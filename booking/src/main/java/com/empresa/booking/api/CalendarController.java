package com.empresa.booking.api;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.empresa.booking.application.GetVesselCalendarReadModelUseCase;
import com.empresa.booking.application.VesselCalendar;

/** T048. FR-001, FR-002 — read-model replicado, nunca calcula disponibilidade própria. */
@RestController
@RequestMapping("/vessels/{vesselId}/calendar")
public class CalendarController {

    private final GetVesselCalendarReadModelUseCase getVesselCalendarReadModelUseCase;

    public CalendarController(GetVesselCalendarReadModelUseCase getVesselCalendarReadModelUseCase) {
        this.getVesselCalendarReadModelUseCase = getVesselCalendarReadModelUseCase;
    }

    @GetMapping
    public VesselCalendar getCalendar(
            @PathVariable String vesselId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return getVesselCalendarReadModelUseCase.getCalendar(vesselId, from, to);
    }
}
