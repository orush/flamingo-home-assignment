package com.flamingo.qa.data;

import com.flamingo.qa.api.booker.model.Booking;
import com.flamingo.qa.api.booker.model.BookingDates;
import com.flamingo.qa.ui.model.WebTableRecord;

import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

/** Builds valid, unique test data. Every API test seeds its own booking. */
public final class TestDataFactory {

    private static final String[] FIRST_NAMES = {"Ada", "Grace", "Alan", "Edsger", "Barbara"};
    private static final String[] LAST_NAMES = {"Lovelace", "Hopper", "Turing", "Dijkstra", "Liskov"};
    private static final String[] DEPARTMENTS = {"Engineering", "Research", "Operations", "Finance"};

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

    /**
     * A valid web table record. The email carries a random suffix so a record is
     * unambiguous even when names repeat, and uses the reserved example.com domain.
     */
    public static WebTableRecord randomWebTableRecord() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String firstName = pick(FIRST_NAMES);
        return WebTableRecord.builder()
                .firstName(firstName)
                .lastName(pick(LAST_NAMES))
                .age(String.valueOf(random.nextInt(18, 80)))
                .email(firstName.toLowerCase() + "." + Long.toString(random.nextLong(1L << 40), 36) + "@example.com")
                .salary(String.valueOf(random.nextInt(1_000, 200_000)))
                .department(pick(DEPARTMENTS))
                .build();
    }

    private static String pick(String[] values) {
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }
}
