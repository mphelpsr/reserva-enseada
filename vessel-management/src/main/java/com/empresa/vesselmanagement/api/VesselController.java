package com.empresa.vesselmanagement.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.empresa.vesselmanagement.application.RegisterVesselCommand;
import com.empresa.vesselmanagement.application.RegisterVesselUseCase;
import com.empresa.vesselmanagement.application.RemoveVesselUseCase;
import com.empresa.vesselmanagement.application.TransferVesselResult;
import com.empresa.vesselmanagement.application.TransferVesselUseCase;
import com.empresa.vesselmanagement.application.UpdateVesselCommand;
import com.empresa.vesselmanagement.application.UpdateVesselUseCase;
import com.empresa.vesselmanagement.domain.vessel.Vessel;

/** T047. FR-001, FR-002. */
@RestController
@RequestMapping("/vessels")
public class VesselController {

    private final RegisterVesselUseCase registerVesselUseCase;
    private final UpdateVesselUseCase updateVesselUseCase;
    private final TransferVesselUseCase transferVesselUseCase;
    private final RemoveVesselUseCase removeVesselUseCase;

    public VesselController(
            RegisterVesselUseCase registerVesselUseCase,
            UpdateVesselUseCase updateVesselUseCase,
            TransferVesselUseCase transferVesselUseCase,
            RemoveVesselUseCase removeVesselUseCase) {
        this.registerVesselUseCase = registerVesselUseCase;
        this.updateVesselUseCase = updateVesselUseCase;
        this.transferVesselUseCase = transferVesselUseCase;
        this.removeVesselUseCase = removeVesselUseCase;
    }

    @PostMapping
    public ResponseEntity<Vessel> register(@RequestBody RegisterVesselRequest request) {
        Vessel vessel = registerVesselUseCase.register(new RegisterVesselCommand(
                request.ownerId(), request.nomeLegal(), request.nomeFantasia(),
                request.numeroRegistroCapitania(), request.cpfCnpjProprietario(),
                request.capacidadeMaxima(), request.portoSaida()));
        return ResponseEntity.status(HttpStatus.CREATED).body(vessel);
    }

    @PatchMapping("/{id}")
    public Vessel update(@PathVariable String id, @RequestBody UpdateVesselRequest request) {
        return updateVesselUseCase.update(id, new UpdateVesselCommand(request.nomeFantasia(), request.portoSaida(), request.status()));
    }

    @PostMapping("/{id}/transfer")
    public TransferVesselResult transfer(@PathVariable String id, @RequestBody TransferRequest request) {
        return transferVesselUseCase.transfer(id, request.targetVesselId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable String id) {
        removeVesselUseCase.remove(id);
        return ResponseEntity.noContent().build();
    }
}
