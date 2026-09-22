package com.flamingo.qa.api;

import com.flamingo.qa.api.booker.BookingClient;
import com.flamingo.qa.api.booker.TokenProvider;
import com.flamingo.qa.api.booker.model.Booking;
import com.flamingo.qa.api.booker.model.CreateBookingResponse;
import com.flamingo.qa.data.TestDataFactory;
import com.flamingo.qa.junit.ApiTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Restful Booker")
@Feature("Booking CRUD")
class BookingCrudTest {

    private final BookingClient bookings = new BookingClient();
    private Integer createdId;

    @AfterEach
    void removeCreatedBooking() {
        if (createdId != null) {
            bookings.delete(createdId, TokenProvider.token());
            createdId = null;
        }
    }

    private int seedBooking(Booking booking) {
        ApiResponse<CreateBookingResponse> created = bookings.create(booking);
        assertThat(created.statusCode()).isEqualTo(200);
        createdId = created.body().getBookingid();
        return createdId;
    }

    @ApiTest
    @DisplayName("Creating a booking returns an id and echoes the submitted payload")
    void createsBooking() {
        Booking booking = TestDataFactory.randomBooking();

        ApiResponse<CreateBookingResponse> response = bookings.create(booking);
        createdId = response.body().getBookingid();

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
        createdId = null; // already removed; skip @AfterEach cleanup

        // Documented quirk: this API answers a successful DELETE with 201 Created.
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(bookings.getById(id).statusCode()).isEqualTo(404);
    }
}
