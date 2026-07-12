package com.empresa.vesselmanagement.jobs;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.empresa.vesselmanagement.domain.advisory.AdvisoryCondition;
import com.empresa.vesselmanagement.domain.advisory.WeatherTideAdvisory;
import com.empresa.vesselmanagement.domain.vessel.Vessel;
import com.empresa.vesselmanagement.infrastructure.dynamodb.AdvisoryRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.VesselRepository;
import com.empresa.vesselmanagement.infrastructure.external.StormglassClient;

/**
 * T057. Acionado pelo EventBridge Scheduler (infra/eventbridge.tf, T006) via
 * SPRING_CLOUD_FUNCTION_DEFINITION=advisoryCalculationJob (infra/lambda.tf) — o bean
 * Function do Spring Cloud Function com esse nome é declarado em
 * VesselManagementApplication, não aqui (nome de bean "advisoryCalculationJobService"
 * pra não colidir com o nome da function).
 * Popula WeatherTideAdvisory via StormglassClient (T056) para o dia de hoje de
 * cada embarcação — NUNCA escreve em DeclaredAvailability (Princípio I): o
 * advisory é só um alerta, exposto por GetAdvisoryUseCase (T045).
 *
 * Embarcações sem `latitude`/`longitude` cadastrados são puladas — FR-001 não
 * captura coordenadas hoje, só o texto livre `portoSaida` (ver Vessel.java).
 */
@Component("advisoryCalculationJobService")
public class AdvisoryCalculationJob {

    private static final Logger log = LoggerFactory.getLogger(AdvisoryCalculationJob.class);

    private final VesselRepository vesselRepository;
    private final StormglassClient stormglassClient;
    private final AdvisoryRepository advisoryRepository;

    public AdvisoryCalculationJob(
            VesselRepository vesselRepository, StormglassClient stormglassClient, AdvisoryRepository advisoryRepository) {
        this.vesselRepository = vesselRepository;
        this.stormglassClient = stormglassClient;
        this.advisoryRepository = advisoryRepository;
    }

    public void run() {
        LocalDate today = LocalDate.now();
        List<Vessel> vessels = vesselRepository.findAll();
        log.info("AdvisoryCalculationJob: recalculando advisory de {} para {} embarcações", today, vessels.size());

        for (Vessel vessel : vessels) {
            if (vessel.getLatitude() == null || vessel.getLongitude() == null) {
                log.debug("Embarcação {} sem coordenadas — advisory pulado", vessel.getId());
                continue;
            }

            Optional<AdvisoryCondition> condition =
                    stormglassClient.fetchCondition(vessel.getLatitude(), vessel.getLongitude(), today);

            condition.ifPresent(cond -> advisoryRepository.save(WeatherTideAdvisory.builder()
                    .vesselId(vessel.getId())
                    .data(today)
                    .condicao(cond)
                    .detalhes("Stormglass — waveHeight/windSpeed, limiares heurísticos (ver StormglassClient)")
                    .build()));
        }
    }
}
