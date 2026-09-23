package com.flamingo.qa.data;

import com.flamingo.qa.api.booker.model.Booking;
import com.flamingo.qa.api.booker.model.BookingDates;
import com.flamingo.qa.ui.model.Gender;
import com.flamingo.qa.ui.model.Hobby;
import com.flamingo.qa.ui.model.StudentRegistration;
import com.flamingo.qa.ui.model.WebTableRecord;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    /**
     * Every field on the practice form filled in. The birthday has a single-digit
     * day on purpose: the confirmation zero-pads it ("05 March,1985"), and only a
     * single-digit day exercises that.
     */
    public static StudentRegistration completeRegistration() {
        return minimalRegistration().toBuilder()
                .email("grace.hopper@example.com")
                .dateOfBirth(LocalDate.of(1985, 3, 5))
                .subject("Maths")
                .subject("Physics")
                .hobby(Hobby.SPORTS)
                .hobby(Hobby.MUSIC)
                .picture(fixture("avatar.png"))
                .currentAddress("1 Compiler Way")
                .state("NCR")
                .city("Delhi")
                .build();
    }

    /** Only the fields the form requires: names, gender and a ten-digit mobile. */
    public static StudentRegistration minimalRegistration() {
        return StudentRegistration.builder()
                .firstName("Grace")
                .lastName("Hopper")
                .gender(Gender.FEMALE)
                .mobile("5550001234")
                .build();
    }

    /** A file from {@code src/main/resources/fixtures}, resolved from the classpath. */
    public static Path fixture(String name) {
        try {
            return Paths.get(TestDataFactory.class.getResource("/fixtures/" + name).toURI());
        } catch (URISyntaxException | NullPointerException e) {
            throw new IllegalArgumentException("Fixture not found on the classpath: " + name, e);
        }
    }

    private static String pick(String[] values) {
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }
}
