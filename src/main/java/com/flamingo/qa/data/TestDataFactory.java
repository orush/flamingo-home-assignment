package com.flamingo.qa.data;

import com.flamingo.qa.api.booker.model.Booking;
import com.flamingo.qa.api.booker.model.BookingDates;

import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

/** Builds valid, unique test data. Every API test seeds its own booking. */
public final class TestDataFactory {

    private static final String[] FIRST_NAMES = {"Ada", "Grace", "Alan", "Edsger", "Barbara"};
    private static final String[] LAST_NAMES = {"Lovelace", "Hopper", "Turing", "Dijkstra", "Liskov"};

    private TestDataFactory() {
    }

    public static Booking randomBooking() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        LocalDate checkin = LocalDate.now().plusDays(random.nextInt(1, 30));
        return Booking.builder()
                .firstname(pick(FIRST_NAMES))
                .lastname(pick(LAST_NAMES))
                .totalprice(random.nextInt(50, 5000))
                .depositpaid(random.nextBoolean())
                .bookingdates(BookingDates.builder()
                        .checkin(checkin)
                        .checkout(checkin.plusDays(random.nextInt(1, 14)))
                        .build())
                .additionalneeds("Breakfast")
                .build();
    }

    private static String pick(String[] values) {
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }
}
