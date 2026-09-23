package com.flamingo.qa.api;

import com.flamingo.qa.api.booker.BookingClient;
import com.flamingo.qa.api.booker.TokenProvider;
import com.flamingo.qa.api.booker.model.Booking;
import com.flamingo.qa.api.booker.model.CreateBookingResponse;
import com.flamingo.qa.data.TestDataFactory;
import com.flamingo.qa.junit.ApiTest;
import com.flamingo.qa.junit.RetryOnNetworkError;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * Booking lifecycle against the live service.
 *
 * <p>Each test seeds the data it needs and asserts only on that data, so the
 * tests are independent and safe to run concurrently. They deliberately do
 * <em>not</em> delete what they create: the service resets periodically by
 * design, teardown is not a requirement, and an extra DELETE per test is load
 * on a public service the brief asks us not to overload.
 *
 * <p>Every call carries a token, which is how a client of a booking API should
 * behave. Note that this API only <em>enforces</em> it on PUT, PATCH and DELETE;
 * that gap is covered as a finding in {@code BookingAuthorizationTest} rather
 * than being silently accommodated here.
 */
@Epic("Restful Booker")
@Feature("Booking CRUD")
class BookingCrudTest {

    private final BookingClient bookings = new BookingClient();

    private int seedBooking(Booking booking) {
        ApiResponse<CreateBookingResponse> created =
                bookings.create(booking, TokenProvider.token());
        assertThat(created.statusCode()).isEqualTo(200);
        return created.body().getBookingid();
    }

    @RetryOnNetworkError
    @Tag("api")
    @DisplayName("Creating a booking returns an id and echoes the submitted payload")
    void createsBooking() {
        Booking booking = TestDataFactory.randomBooking();

        ApiResponse<CreateBookingResponse> response =
                bookings.create(booking, TokenProvider.token());

        // Hard: the body below is only a booking when the create succeeded.
        assertThat(response.statusCode()).isEqualTo(200);
        assertSoftly(softly -> {
            softly.assertThat(response.body().getBookingid()).isPositive();
            softly.assertThat(response.body().getBooking()).isEqualTo(booking);
        });
    }

    @ApiTest
    @DisplayName("Retrieving a booking by id returns the created booking")
    void retrievesBookingById() {
        Booking booking = TestDataFactory.randomBooking();
        int id = seedBooking(booking);

        ApiResponse<Booking> response = bookings.getById(id, TokenProvider.token());

        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(200);
            softly.assertThat(response.body()).isEqualTo(booking);
        });
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

        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(200);
            softly.assertThat(response.body()).isEqualTo(updated);
            softly.assertThat(bookings.getById(id, TokenProvider.token()).body()).isEqualTo(updated);
        });
    }

    @ApiTest
    @DisplayName("Partially updating a booking changes only the supplied fields")
    void partiallyUpdatesBooking() {
        Booking original = TestDataFactory.randomBooking();
        int id = seedBooking(original);

        ApiResponse<Booking> response = bookings.patch(
                id, Map.of("firstname", "Patched", "totalprice", 4242), TokenProvider.token());

        assertThat(response.statusCode()).isEqualTo(200);

        Booking patched = response.body();
        assertSoftly(softly -> {
            softly.assertThat(patched.getFirstname()).isEqualTo("Patched");
            softly.assertThat(patched.getTotalprice()).isEqualTo(4242);
            // Everything not named in the request survives untouched — the whole
            // point of PATCH over PUT.
            softly.assertThat(patched.getLastname()).isEqualTo(original.getLastname());
            softly.assertThat(patched.getDepositpaid()).isEqualTo(original.getDepositpaid());
            softly.assertThat(patched.getBookingdates()).isEqualTo(original.getBookingdates());
            softly.assertThat(patched.getAdditionalneeds()).isEqualTo(original.getAdditionalneeds());
        });
    }

    @ApiTest
    @DisplayName("Deleting a booking returns 201 and the booking is then gone")
    void deletesBooking() {
        int id = seedBooking(TestDataFactory.randomBooking());

        ApiResponse<Void> response = bookings.delete(id, TokenProvider.token());

        // Documented quirk: this API answers a successful DELETE with 201 Created.
        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(201);
            softly.assertThat(bookings.getById(id, TokenProvider.token()).statusCode()).isEqualTo(404);
        });
    }
}
