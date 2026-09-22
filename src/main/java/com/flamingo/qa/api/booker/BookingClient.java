package com.flamingo.qa.api.booker;

import com.flamingo.qa.api.ApiResponse;
import com.flamingo.qa.api.RestClientFactory;
import com.flamingo.qa.api.booker.model.Booking;
import com.flamingo.qa.api.booker.model.CreateBookingResponse;
import io.qameta.allure.Step;
import io.restassured.response.Response;

/**
 * Restful Booker booking endpoints.
 *
 * <p>Mutating calls authenticate with a {@code token} cookie, which is what this
 * API expects in place of a bearer header.
 */
public class BookingClient {

    private static final String BY_ID = "/booking/{id}";

    @Step("Create booking")
    public ApiResponse<CreateBookingResponse> create(Booking booking) {
        Response response = RestClientFactory.booker().body(booking).post("/booking");
        return ApiResponse.from(response, CreateBookingResponse.class);
    }

    @Step("Get booking {id}")
    public ApiResponse<Booking> getById(int id) {
        Response response = RestClientFactory.booker().get(BY_ID, id);
        return ApiResponse.from(response, Booking.class);
    }

    @Step("Update booking {id}")
    public ApiResponse<Booking> update(int id, Booking booking, String token) {
        Response response = RestClientFactory.booker()
                .cookie("token", token)
                .body(booking)
                .put(BY_ID, id);
        return ApiResponse.from(response, Booking.class);
    }

    /** Update without credentials. Used to prove the endpoint is protected. */
    @Step("Update booking {id} without a token")
    public ApiResponse<Booking> updateWithoutToken(int id, Booking booking) {
        Response response = RestClientFactory.booker().body(booking).put(BY_ID, id);
        return ApiResponse.from(response, Booking.class);
    }

    @Step("Delete booking {id}")
    public ApiResponse<Void> delete(int id, String token) {
        Response response = RestClientFactory.booker().cookie("token", token).delete(BY_ID, id);
        return ApiResponse.from(response, Void.class);
    }
}
