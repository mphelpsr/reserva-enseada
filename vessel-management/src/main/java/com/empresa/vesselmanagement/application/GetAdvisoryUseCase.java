package com.empresa.vesselmanagement.application;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.empresa.vesselmanagement.application.exception.AdvisoryNotFoundException;
import com.empresa.vesselmanagement.application.exception.VesselNotFoundException;
import com.empresa.vesselmanagement.domain.advisory.WeatherTideAdvisory;
import com.empresa.vesselmanagement.infrastructure.dynamodb.AdvisoryRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.VesselRepository;

/**
 * T045. FR-006, FR-008: expõe o advisory (alerta) SEM nunca alterar
 * DeclaredAvailability — estritamente leitura (Princípio I). A escrita é
 * exclusiva do job assíncrono AdvisoryCalculationJob (T057, Fase 3.4).
 */
@Service
public class GetAdvisoryUseCase {

    private final VesselRepository vesselRepository;
    private final AdvisoryRepository advisoryRepository;

    public GetAdvisoryUseCase(VesselRepository vesselRepository, AdvisoryRepository advisoryRepository) {
        this.vesselRepository = vesselRepository;
        this.advisoryRepository = advisoryRepository;
    }

    public WeatherTideAdvisory getAdvisory(String vesselId, LocalDate data) {
        vesselRepository.findById(vesselId).orElseThrow(() -> new VesselNotFoundException(vesselId));
        return advisoryRepository.findByVesselDate(vesselId, data)
                .orElseThrow(() -> new AdvisoryNotFoundException(vesselId, data.toString()));
    }
}
