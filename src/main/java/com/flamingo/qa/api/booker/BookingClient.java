package com.flamingo.qa.api.booker;

import com.fasterxml.jackson.databind.JsonNode;
import com.flamingo.qa.api.ApiResponse;
import com.flamingo.qa.api.RestClientFactory;
import com.flamingo.qa.api.booker.model.Booking;
import com.flamingo.qa.api.booker.model.BookingId;
import com.flamingo.qa.api.booker.model.CreateBookingResponse;
import com.flamingo.qa.config.Secret;
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
    private RequestSpecification spec(Secret token) {
        RequestSpecification spec = RestClientFactory.booker();
        return token == null ? spec : spec.cookie("token", token.reveal());
    }

    @Step("Create booking")
    public ApiResponse<CreateBookingResponse> create(Booking booking, Secret token) {
        Response response = spec(token).body(booking).post(COLLECTION);
        return ApiResponse.from(response, CreateBookingResponse.class);
    }

    @Step("Create booking without a token")
    public ApiResponse<CreateBookingResponse> createWithoutToken(Booking booking) {
        return create(booking, null);
    }

    @Step("List booking ids")
    public ApiResponse<BookingId[]> getAll(Secret token) {
        Response response = spec(token).get(COLLECTION);
        return ApiResponse.from(response, BookingId[].class);
    }

    @Step("List booking ids without a token")
    public ApiResponse<BookingId[]> getAllWithoutToken() {
        return getAll(null);
    }

    @Step("Get booking {id}")
    public ApiResponse<Booking> getById(int id, Secret token) {
        Response response = spec(token).get(BY_ID, id);
        return ApiResponse.from(response, Booking.class);
    }

    /** Fetch by an arbitrary path segment, so non-numeric ids can be exercised. */
    @Step("Get booking with raw id {id}")
    public ApiResponse<Booking> getByRawId(String id, Secret token) {
        Response response = spec(token).get(BY_ID, id);
        return ApiResponse.from(response, Booking.class);
    }

    @Step("Get booking {id} without a token")
    public ApiResponse<Booking> getByIdWithoutToken(int id) {
        return getById(id, null);
    }

    /**
     * Create from an arbitrary payload.
     *
     * <p>Returns a {@link JsonNode} rather than a typed model because invalid
     * payloads come back with values the model cannot hold — a string where an
     * integer belongs, for instance. Accepts a String body for malformed JSON.
     */
    @Step("Create booking from a raw payload")
    public ApiResponse<JsonNode> createRaw(Object body, Secret token) {
        Response response = spec(token).body(body).post(COLLECTION);
        return ApiResponse.from(response, JsonNode.class);
    }

    /** Update from an arbitrary payload, for incomplete or malformed bodies. */
    @Step("Update booking {id} from a raw payload")
    public ApiResponse<JsonNode> updateRaw(int id, Object body, Secret token) {
        Response response = spec(token).body(body).put(BY_ID, id);
        return ApiResponse.from(response, JsonNode.class);
    }

    @Step("Update booking {id}")
    public ApiResponse<Booking> update(int id, Booking booking, Secret token) {
        Response response = spec(token).body(booking).put(BY_ID, id);
        return ApiResponse.from(response, Booking.class);
    }

    @Step("Update booking {id} without a token")
    public ApiResponse<Booking> updateWithoutToken(int id, Booking booking) {
        return update(id, booking, null);
    }

    @Step("Partially update booking {id}")
    public ApiResponse<Booking> patch(int id, Map<String, Object> fields, Secret token) {
        Response response = spec(token).body(fields).patch(BY_ID, id);
        return ApiResponse.from(response, Booking.class);
    }

    @Step("Partially update booking {id} without a token")
    public ApiResponse<Booking> patchWithoutToken(int id, Map<String, Object> fields) {
        return patch(id, fields, null);
    }

    @Step("Delete booking {id}")
    public ApiResponse<Void> delete(int id, Secret token) {
        Response response = spec(token).delete(BY_ID, id);
        return ApiResponse.from(response, Void.class);
    }

    @Step("Delete booking {id} without a token")
    public ApiResponse<Void> deleteWithoutToken(int id) {
        return delete(id, null);
    }
}
