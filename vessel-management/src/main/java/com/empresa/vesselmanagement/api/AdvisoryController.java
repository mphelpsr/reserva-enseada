package com.empresa.vesselmanagement.api;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.empresa.vesselmanagement.application.GetAdvisoryUseCase;
import com.empresa.vesselmanagement.domain.advisory.WeatherTideAdvisory;

/** T051. FR-006, FR-008 — leitura, nunca altera DeclaredAvailability (Princípio I). */
@RestController
@RequestMapping("/vessels/{vesselId}/advisory")
public class AdvisoryController {

    private final GetAdvisoryUseCase getAdvisoryUseCase;

    public AdvisoryController(GetAdvisoryUseCase getAdvisoryUseCase) {
        this.getAdvisoryUseCase = getAdvisoryUseCase;
    }

    @GetMapping("/{data}")
    public WeatherTideAdvisory getAdvisory(@PathVariable String vesselId, @PathVariable LocalDate data) {
        return getAdvisoryUseCase.getAdvisory(vesselId, data);
    }
}
