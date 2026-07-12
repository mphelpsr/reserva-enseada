package com.empresa.booking.application;

import com.empresa.booking.domain.booking.BookingStatus;

public record CancelBookingResult(BookingStatus status, boolean reembolsoIntegral) {
}
