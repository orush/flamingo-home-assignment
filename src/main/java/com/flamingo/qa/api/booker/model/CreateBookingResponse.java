package com.flamingo.qa.api.booker.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class CreateBookingResponse {
    Integer bookingid;
    Booking booking;
}
