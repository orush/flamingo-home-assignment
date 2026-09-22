package com.flamingo.qa.api;

import com.flamingo.qa.api.booker.BookingClient;
import com.flamingo.qa.api.booker.TokenProvider;
import com.flamingo.qa.api.booker.model.Booking;
import com.flamingo.qa.api.booker.model.CreateBookingResponse;
import com.flamingo.qa.data.TestDataFactory;
import com.flamingo.qa.junit.ApiTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Booking lifecycle against the live service.
 *
 * <p>Each test seeds the data it needs and asserts only on that data, so the
 * tests are independent and safe to run concurrently. They deliberately do
 * <em>not</em> delete what they create: the service resets periodically by
 * design, teardown is not a requirement, and an extra DELETE per test is load
 * on a public service the brief asks us not to overload.
 *
 * <p>Note which calls carry a token. This API leaves POST and GET
 * unauthenticated and protects only PUT and DELETE, so passing credentials to
 * the first two would assert a rule the API does not enforce.
 */
@Epic("Restful Booker")
@Feature("Booking CRUD")
class BookingCrudTest {

    private final BookingClient bookings = new BookingClient();

    private int seedBooking(Booking booking) {
        ApiResponse<CreateBookingResponse> created = bookings.create(booking);
        assertThat(created.statusCode()).isEqualTo(200);
        return created.body().getBookingid();
    }

    @ApiTest
    @DisplayName("Creating a booking returns an id and echoes the submitted payload")
    void createsBooking() {
        Booking booking = TestDataFactory.randomBooking();

        ApiResponse<CreateBookingResponse> response = bookings.create(booking);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().getBookingid()).isPositive();
        assertThat(response.body().getBooking()).isEqualTo(booking);
    }

    @ApiTest
    @DisplayName("Retrieving a booking by id returns the created booking")
    void retrievesBookingById() {
        Booking booking = TestDataFactory.randomBooking();
        int id = seedBooking(booking);

        ApiResponse<Booking> response = bookings.getById(id);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(booking);
    }

    @ApiTest
    @DisplayName("Updating a booking replaces its fields")
    void updatesBooking() {
        int id = seedBooking(TestDataFactory.randomBooking());
        Booking updated = TestDataFactory.randomBooking().toBuilder()
                .firstname("Updated")
                .lastname("Guest")
                .totalprice(999)
                .build();

        ApiResponse<Booking> response = bookings.update(id, updated, TokenProvider.token());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(updated);
        assertThat(bookings.getById(id).body()).isEqualTo(updated);
    }

    @ApiTest
    @DisplayName("Deleting a booking returns 201 and the booking is then gone")
    void deletesBooking() {
        int id = seedBooking(TestDataFactory.randomBooking());

        ApiResponse<Void> response = bookings.delete(id, TokenProvider.token());

        // Documented quirk: this API answers a successful DELETE with 201 Created.
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(bookings.getById(id).statusCode()).isEqualTo(404);
    }
}
