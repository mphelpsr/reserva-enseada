package com.empresa.booking.application;

public record ConfirmBookingCommand(String holdId, String paymentReference) {
}
