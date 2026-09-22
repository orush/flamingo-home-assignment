package com.flamingo.qa.api;

import com.flamingo.qa.api.booker.BookingClient;
import com.flamingo.qa.api.booker.TokenProvider;
import com.flamingo.qa.api.booker.model.Booking;
import com.flamingo.qa.api.booker.model.CreateBookingResponse;
import com.flamingo.qa.data.TestDataFactory;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Restful Booker")
@Feature("Booking CRUD")
class BookingDataDrivenTest {

    private final BookingClient bookings = new BookingClient();

    // @ApiTest implies @Test, which cannot combine with @ParameterizedTest,
    // so the tag is applied directly here.
    @Tag("api")
    @ParameterizedTest(name = "deposit paid={0}, additional needs={1}")
    @CsvSource({
            "true,  Breakfast",
            "false, Breakfast",
            "true,  ",
            "false, "
    })
    void createsBookingsWithVaryingOptionalFields(boolean depositPaid, String additionalNeeds) {
        Booking booking = TestDataFactory.randomBooking().toBuilder()
                .depositpaid(depositPaid)
                .additionalneeds(additionalNeeds)
                .build();

        ApiResponse<CreateBookingResponse> response =
                bookings.create(booking, TokenProvider.token());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().getBookingid()).isPositive();
        assertThat(response.body().getBooking().getDepositpaid()).isEqualTo(depositPaid);
        assertThat(response.body().getBooking().getAdditionalneeds()).isEqualTo(additionalNeeds);
    }
}
