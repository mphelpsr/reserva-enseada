package com.empresa.booking.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.empresa.booking.application.CancelBookingByBuyerUseCase;
import com.empresa.booking.application.CancelBookingResult;
import com.empresa.booking.application.ConfirmBookingCommand;
import com.empresa.booking.application.ConfirmBookingUseCase;
import com.empresa.booking.application.CreateHoldCommand;
import com.empresa.booking.application.CreateHoldResult;
import com.empresa.booking.application.CreateHoldUseCase;
import com.empresa.booking.application.GetBookingUseCase;
import com.empresa.booking.application.ListBuyerBookingsUseCase;
import com.empresa.booking.application.RespondToTransferUseCase;
import com.empresa.booking.domain.availability.TourType;
import com.empresa.booking.domain.booking.Booking;

/** T047. FR-003 a FR-011. */
@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final CreateHoldUseCase createHoldUseCase;
    private final ConfirmBookingUseCase confirmBookingUseCase;
    private final CancelBookingByBuyerUseCase cancelBookingByBuyerUseCase;
    private final RespondToTransferUseCase respondToTransferUseCase;
    private final ListBuyerBookingsUseCase listBuyerBookingsUseCase;
    private final GetBookingUseCase getBookingUseCase;

    public BookingController(
            CreateHoldUseCase createHoldUseCase,
            ConfirmBookingUseCase confirmBookingUseCase,
            CancelBookingByBuyerUseCase cancelBookingByBuyerUseCase,
            RespondToTransferUseCase respondToTransferUseCase,
            ListBuyerBookingsUseCase listBuyerBookingsUseCase,
            GetBookingUseCase getBookingUseCase) {
        this.createHoldUseCase = createHoldUseCase;
        this.confirmBookingUseCase = confirmBookingUseCase;
        this.cancelBookingByBuyerUseCase = cancelBookingByBuyerUseCase;
        this.respondToTransferUseCase = respondToTransferUseCase;
        this.listBuyerBookingsUseCase = listBuyerBookingsUseCase;
        this.getBookingUseCase = getBookingUseCase;
    }

    @PostMapping("/hold")
    public ResponseEntity<CreateHoldResult> createHold(@RequestBody CreateHoldRequest request) {
        CreateHoldResult result = createHoldUseCase.createHold(new CreateHoldCommand(
                request.buyerId(),
                request.vesselId(),
                LocalDate.parse(request.data()),
                TourType.fromValue(request.tipoPasseio()),
                request.quantidade()));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/{holdId}/confirm")
    public BookingResponse confirm(@PathVariable String holdId, @RequestBody ConfirmBookingRequest request) {
        Booking booking = confirmBookingUseCase.confirm(new ConfirmBookingCommand(holdId, request.paymentReference()));
        return BookingResponse.from(booking);
    }

    @PostMapping("/{id}/cancel")
    public CancelBookingResult cancel(@PathVariable String id) {
        return cancelBookingByBuyerUseCase.cancel(id);
    }

    @PostMapping("/{id}/respond-transfer")
    public BookingResponse respondTransfer(@PathVariable String id, @RequestBody RespondTransferRequest request) {
        Booking booking = respondToTransferUseCase.respond(id, request.aceitar());
        return BookingResponse.from(booking);
    }

    @GetMapping
    public List<BookingResponse> list(@RequestParam String buyerId) {
        return listBuyerBookingsUseCase.list(buyerId).stream().map(BookingResponse::from).toList();
    }

    @GetMapping("/{id}")
    public BookingResponse get(@PathVariable String id) {
        return BookingResponse.from(getBookingUseCase.get(id));
    }
}
