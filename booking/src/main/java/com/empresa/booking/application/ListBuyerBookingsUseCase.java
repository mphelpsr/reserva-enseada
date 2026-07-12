package com.empresa.booking.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.empresa.booking.domain.booking.Booking;
import com.empresa.booking.infrastructure.dynamodb.BookingRepository;

/** T044. FR-011: histórico do comprador, via GSI1 (BookingRepository.findByBuyerId). */
@Service
public class ListBuyerBookingsUseCase {

    private final BookingRepository bookingRepository;

    public ListBuyerBookingsUseCase(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public List<Booking> list(String buyerId) {
        return bookingRepository.findByBuyerId(buyerId);
    }
}
