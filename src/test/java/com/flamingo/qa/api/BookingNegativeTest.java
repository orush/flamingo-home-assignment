package com.flamingo.qa.api;

import com.flamingo.qa.api.booker.BookingClient;
import com.flamingo.qa.api.booker.TokenProvider;
import com.flamingo.qa.api.booker.model.Booking;
import com.flamingo.qa.data.TestDataFactory;
import com.flamingo.qa.junit.ApiTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Restful Booker")
@Feature("Booking error handling")
class BookingNegativeTest {

    private static final int ABSENT_BOOKING_ID = 99_999_999;

    private final BookingClient bookings = new BookingClient();

    @ApiTest
    @DisplayName("Retrieving a non-existent booking returns 404 with a plain-text body")
    void returnsNotFoundForUnknownId() {
        ApiResponse<Booking> response =
                bookings.getById(ABSENT_BOOKING_ID, TokenProvider.token());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.rawBody()).isEqualTo("Not Found");
        assertThat(response.body()).isNull();
    }

    @ApiTest
    @DisplayName("Updating a booking without a token is rejected with 403")
    void rejectsUpdateWithoutToken() {
        ApiResponse<Booking> response =
                bookings.updateWithoutToken(ABSENT_BOOKING_ID, TestDataFactory.randomBooking());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.rawBody()).isEqualTo("Forbidden");
    }
}
