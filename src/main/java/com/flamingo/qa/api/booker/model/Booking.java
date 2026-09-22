package com.flamingo.qa.api.booker.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * A booking.
 *
 * <p>{@code NON_NULL} matters: the API omits {@code additionalneeds} from its
 * response when it was not sent, so without it a round-tripped booking would
 * not equal the submitted one.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Booking {
    String firstname;
    String lastname;
    Integer totalprice;
    Boolean depositpaid;
    BookingDates bookingdates;
    String additionalneeds;
}
