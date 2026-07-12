package com.empresa.vesselmanagement.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.empresa.vesselmanagement.application.exception.InvalidVesselDataException;
import com.empresa.vesselmanagement.domain.vessel.Vessel;
import com.empresa.vesselmanagement.domain.vessel.VesselStatus;
import com.empresa.vesselmanagement.infrastructure.dynamodb.VesselRepository;

/**
 * T038. FR-001: cadastro de embarcação, status inicial `pendente_configuracao`
 * (Acceptance Scenario 1) até que ao menos um dia de disponibilidade exista.
 * FR-009: identificador único (nº registro Capitania + CPF/CNPJ + nome legal)
 * validado por VesselRepository.create via TransactWriteItems.
 *
 * Não valida `payment_recebedor_id` aqui — FR-016 só bloqueia a ATIVAÇÃO
 * (UpdateVesselUseCase), o cadastro em si sempre nasce `pendente_configuracao`.
 */
@Service
public class RegisterVesselUseCase {

    private final VesselRepository vesselRepository;

    public RegisterVesselUseCase(VesselRepository vesselRepository) {
        this.vesselRepository = vesselRepository;
    }

    public Vessel register(RegisterVesselCommand command) {
        validate(command);

        Vessel vessel = Vessel.builder()
                .id(UUID.randomUUID().toString())
                .ownerId(command.ownerId())
                .nomeLegal(command.nomeLegal())
                .nomeFantasia(command.nomeFantasia())
                .numeroRegistroCapitania(command.numeroRegistroCapitania())
                .cpfCnpjProprietario(command.cpfCnpjProprietario())
                .capacidadeMaxima(command.capacidadeMaxima())
                .portoSaida(command.portoSaida())
                .status(VesselStatus.PENDENTE_CONFIGURACAO)
                .build();

        vesselRepository.create(vessel);
        return vessel;
    }

    private void validate(RegisterVesselCommand command) {
        if (isBlank(command.nomeLegal())) {
            throw new InvalidVesselDataException("nomeLegal é obrigatório (FR-001)");
        }
        if (command.capacidadeMaxima() == null || command.capacidadeMaxima() <= 0) {
            throw new InvalidVesselDataException("capacidadeMaxima é obrigatória e deve ser positiva (FR-001)");
        }
        if (isBlank(command.portoSaida())) {
            throw new InvalidVesselDataException("portoSaida é obrigatório (FR-001)");
        }
        if (isBlank(command.ownerId())) {
            throw new InvalidVesselDataException("ownerId é obrigatório");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
