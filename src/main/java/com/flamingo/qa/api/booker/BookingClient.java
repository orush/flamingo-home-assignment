package com.flamingo.qa.api.booker;

import com.flamingo.qa.api.ApiResponse;
import com.flamingo.qa.api.RestClientFactory;
import com.flamingo.qa.api.booker.model.Booking;
import com.flamingo.qa.api.booker.model.BookingId;
import com.flamingo.qa.api.booker.model.CreateBookingResponse;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * Restful Booker booking endpoints.
 *
 * <p>Every method comes in two forms: one taking a token and one without. The
 * token is sent as a {@code token} cookie, which is what this API expects in
 * place of a bearer header.
 *
 * <p>The token-less forms exist so authorization coverage can probe each
 * endpoint unauthenticated. See {@code BookingAuthorizationTest}.
 */
public class BookingClient {

    private static final String COLLECTION = "/booking";
    private static final String BY_ID = "/booking/{id}";

    /** Adds the token cookie only when one was supplied. */
    private RequestSpecification spec(String token) {
        RequestSpecification spec = RestClientFactory.booker();
        return token == null ? spec : spec.cookie("token", token);
    }

    @Step("Create booking")
    public ApiResponse<CreateBookingResponse> create(Booking booking, String token) {
        Response response = spec(token).body(booking).post(COLLECTION);
        return ApiResponse.from(response, CreateBookingResponse.class);
    }

    @Step("Create booking without a token")
    public ApiResponse<CreateBookingResponse> createWithoutToken(Booking booking) {
        return create(booking, null);
    }

    @Step("List booking ids")
    public ApiResponse<BookingId[]> getAll(String token) {
        Response response = spec(token).get(COLLECTION);
        return ApiResponse.from(response, BookingId[].class);
    }

    @Step("List booking ids without a token")
    public ApiResponse<BookingId[]> getAllWithoutToken() {
        return getAll(null);
    }

    @Step("Get booking {id}")
    public ApiResponse<Booking> getById(int id, String token) {
        Response response = spec(token).get(BY_ID, id);
        return ApiResponse.from(response, Booking.class);
    }

    @Step("Get booking {id} without a token")
    public ApiResponse<Booking> getByIdWithoutToken(int id) {
        return getById(id, null);
    }

    @Step("Update booking {id}")
    public ApiResponse<Booking> update(int id, Booking booking, String token) {
        Response response = spec(token).body(booking).put(BY_ID, id);
        return ApiResponse.from(response, Booking.class);
    }

    @Step("Update booking {id} without a token")
    public ApiResponse<Booking> updateWithoutToken(int id, Booking booking) {
        return update(id, booking, null);
    }

    @Step("Partially update booking {id}")
    public ApiResponse<Booking> patch(int id, Map<String, Object> fields, String token) {
        Response response = spec(token).body(fields).patch(BY_ID, id);
        return ApiResponse.from(response, Booking.class);
    }

    @Step("Partially update booking {id} without a token")
    public ApiResponse<Booking> patchWithoutToken(int id, Map<String, Object> fields) {
        return patch(id, fields, null);
    }

    @Step("Delete booking {id}")
    public ApiResponse<Void> delete(int id, String token) {
        Response response = spec(token).delete(BY_ID, id);
        return ApiResponse.from(response, Void.class);
    }

    @Step("Delete booking {id} without a token")
    public ApiResponse<Void> deleteWithoutToken(int id) {
        return delete(id, null);
    }
}
