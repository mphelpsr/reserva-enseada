package com.empresa.vesselmanagement.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.empresa.vesselmanagement.application.SetPaymentPixKeyUseCase;
import com.empresa.vesselmanagement.domain.vessel.Owner;

/** T059c. FR-016. */
@RestController
@RequestMapping("/owners/{ownerId}")
public class OwnerController {

    private final SetPaymentPixKeyUseCase setPaymentPixKeyUseCase;

    public OwnerController(SetPaymentPixKeyUseCase setPaymentPixKeyUseCase) {
        this.setPaymentPixKeyUseCase = setPaymentPixKeyUseCase;
    }

    @PutMapping("/payment-pix-key")
    public SetPaymentPixKeyResponse setPaymentPixKey(@PathVariable String ownerId, @RequestBody SetPaymentPixKeyRequest request) {
        Owner owner = setPaymentPixKeyUseCase.setPaymentPixKey(ownerId, request.pixKey());
        return new SetPaymentPixKeyResponse(owner.getId(), owner.getPaymentRecebedorId());
    }
}
