package com.empresa.vesselmanagement.application;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.empresa.vesselmanagement.application.exception.VesselHasFutureBookingsException;
import com.empresa.vesselmanagement.application.exception.VesselNotFoundException;
import com.empresa.vesselmanagement.infrastructure.dynamodb.BookingCountRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.VesselRepository;

/**
 * T040b. FR-002: remoção de embarcação — direta se não há reservas futuras
 * confirmadas; se houver, exige TransferVesselUseCase (T040) concluído antes.
 * Consulta `BookingCountRepository` (réplica `ConfirmedBookingCount`, decisão de
 * 2026-07-12 em plan.md) para decidir.
 */
@Service
public class RemoveVesselUseCase {

    private final VesselRepository vesselRepository;
    private final BookingCountRepository bookingCountRepository;

    public RemoveVesselUseCase(VesselRepository vesselRepository, BookingCountRepository bookingCountRepository) {
        this.vesselRepository = vesselRepository;
        this.bookingCountRepository = bookingCountRepository;
    }

    public void remove(String vesselId) {
        vesselRepository.findById(vesselId).orElseThrow(() -> new VesselNotFoundException(vesselId));

        boolean hasFutureBookings = !bookingCountRepository.findFutureBookingsForVessel(vesselId, LocalDate.now()).isEmpty();
        if (hasFutureBookings) {
            throw new VesselHasFutureBookingsException(vesselId);
        }

        vesselRepository.delete(vesselId);
    }
}
