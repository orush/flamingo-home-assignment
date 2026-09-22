package com.flamingo.qa.api.booker.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** One entry of the {@code GET /booking} collection response. */
@Value
@Builder
@Jacksonized
public class BookingId {
    Integer bookingid;
}
