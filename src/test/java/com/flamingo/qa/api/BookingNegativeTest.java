package com.flamingo.qa.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.flamingo.qa.api.booker.BookingClient;
import com.flamingo.qa.api.booker.TokenProvider;
import com.flamingo.qa.api.booker.model.Booking;
import com.flamingo.qa.data.TestDataFactory;
import com.flamingo.qa.junit.ApiTest;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How the booking endpoints behave when given bad input.
 *
 * <p>Authorization is covered separately in {@code BookingAuthorizationTest};
 * nothing here duplicates it.
 *
 * <p>The validation tests at the bottom are findings. They assert what the
 * service actually does, so the suite stays green, and each names the expected
 * behaviour in its title. Several are worse than a missing check: the service
 * accepts the request and persists corrupted data.
 */
@Epic("Restful Booker")
@Feature("Booking error handling")
class BookingNegativeTest {

    private static final int ABSENT_BOOKING_ID = 99_999_999;

    private final BookingClient bookings = new BookingClient();

    private String token() {
        return TokenProvider.token();
    }

    private int seedBooking() {
        return bookings.create(TestDataFactory.randomBooking(), token()).body().getBookingid();
    }

    /** A structurally valid create payload, so tests can spoil one field at a time. */
    private static Map<String, Object> validPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("firstname", "Ada");
        payload.put("lastname", "Lovelace");
        payload.put("totalprice", 150);
        payload.put("depositpaid", true);
        payload.put("bookingdates", Map.of("checkin", "2026-10-05", "checkout", "2026-10-12"));
        return payload;
    }

    // ---------------------------------------------------------------
    // Resources that do not exist
    // ---------------------------------------------------------------

    @ApiTest
    @Story("Unknown resources")
    @DisplayName("Retrieving a non-existent booking returns 404 with a plain-text body")
    void returnsNotFoundForUnknownId() {
        ApiResponse<Booking> response = bookings.getById(ABSENT_BOOKING_ID, token());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.rawBody()).isEqualTo("Not Found");
        assertThat(response.body())
                .as("no model is produced from a plain-text error body")
                .isNull();
    }

    @ApiTest
    @Story("Unknown resources")
    @DisplayName("A non-numeric booking id returns 404 rather than a parse error")
    void returnsNotFoundForNonNumericId() {
        ApiResponse<Booking> response = bookings.getByRawId("not-a-number", token());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.rawBody()).isEqualTo("Not Found");
    }

    @ApiTest
    @Story("Unknown resources")
    @DisplayName("Updating a non-existent booking returns 405 Method Not Allowed, not 404")
    @Description("Surprising: the resource is missing, so 404 would be the expected answer. "
            + "The suite pins the real behaviour so a change to it is noticed.")
    void rejectsUpdateOfNonExistentBooking() {
        ApiResponse<JsonNode> response =
                bookings.updateRaw(ABSENT_BOOKING_ID, validPayload(), token());

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.rawBody()).isEqualTo("Method Not Allowed");
    }

    @ApiTest
    @Story("Unknown resources")
    @DisplayName("Partially updating a non-existent booking returns 405 Method Not Allowed")
    void rejectsPartialUpdateOfNonExistentBooking() {
        ApiResponse<Booking> response =
                bookings.patch(ABSENT_BOOKING_ID, Map.of("firstname", "Ghost"), token());

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.rawBody()).isEqualTo("Method Not Allowed");
    }

    @ApiTest
    @Story("Unknown resources")
    @DisplayName("Deleting a non-existent booking returns 405 Method Not Allowed")
    void rejectsDeleteOfNonExistentBooking() {
        ApiResponse<Void> response = bookings.delete(ABSENT_BOOKING_ID, token());

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.rawBody()).isEqualTo("Method Not Allowed");
    }

    // ---------------------------------------------------------------
    // Malformed requests, correctly rejected
    // ---------------------------------------------------------------

    @ApiTest
    @Story("Malformed requests")
    @DisplayName("Syntactically invalid JSON is rejected with 400")
    void rejectsMalformedJson() {
        ApiResponse<JsonNode> response = bookings.createRaw("{\"firstname\":", token());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.rawBody()).isEqualTo("Bad Request");
    }

    @ApiTest
    @Story("Malformed requests")
    @DisplayName("A PUT carrying only some fields is rejected with 400")
    @Description("PUT replaces the whole resource, so a partial body is invalid. "
            + "PATCH is the supported way to change one field.")
    void rejectsIncompleteUpdatePayload() {
        int id = seedBooking();

        ApiResponse<JsonNode> response =
                bookings.updateRaw(id, Map.of("firstname", "OnlyThis"), token());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.rawBody()).isEqualTo("Bad Request");
    }

    // ---------------------------------------------------------------
    // Findings: input validation is absent
    // ---------------------------------------------------------------

    @ApiTest
    @Story("Absent input validation")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("FINDING: an empty create body returns 500, not a 400 validation error")
    @Description("A client mistake is reported as a server fault, which hides the cause "
            + "from the caller and pollutes server error metrics.")
    void emptyCreateBodyReturnsServerError() {
        ApiResponse<JsonNode> response = bookings.createRaw(Map.of(), token());

        assertThat(response.statusCode())
                .as("expected 400 Bad Request for an empty payload")
                .isEqualTo(500);
        assertThat(response.rawBody()).isEqualTo("Internal Server Error");
    }

    @ApiTest
    @Story("Absent input validation")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("FINDING: a create missing a required field returns 500, not 400")
    void missingRequiredFieldReturnsServerError() {
        Map<String, Object> payload = validPayload();
        payload.remove("lastname");

        ApiResponse<JsonNode> response = bookings.createRaw(payload, token());

        assertThat(response.statusCode())
                .as("expected 400 Bad Request when lastname is absent")
                .isEqualTo(500);
    }

    @ApiTest
    @Story("Absent input validation")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("FINDING: a non-numeric totalprice is accepted and silently stored as null")
    @Description("The value is not rejected and not preserved. It is discarded without "
            + "telling the caller, so a booking ends up with no price at all.")
    void nonNumericTotalPriceIsSilentlyDiscarded() {
        Map<String, Object> payload = validPayload();
        payload.put("totalprice", "free");

        ApiResponse<JsonNode> response = bookings.createRaw(payload, token());

        assertThat(response.statusCode())
                .as("expected 400 Bad Request for a non-numeric price")
                .isEqualTo(200);
        assertThat(response.body().at("/booking/totalprice").isNull())
                .as("the submitted price was dropped rather than stored or rejected")
                .isTrue();
    }

    @ApiTest
    @Story("Absent input validation")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("FINDING: unparseable dates are accepted and persisted as \"0NaN-aN-aN\"")
    @Description("The worst of the validation gaps: the request succeeds and corrupt data "
            + "is written to the store, where every later reader must cope with it.")
    void unparseableDatesArePersistedCorrupted() {
        Map<String, Object> payload = validPayload();
        payload.put("bookingdates", Map.of("checkin", "not-a-date", "checkout", "also-not-a-date"));

        ApiResponse<JsonNode> response = bookings.createRaw(payload, token());

        assertThat(response.statusCode())
                .as("expected 400 Bad Request for unparseable dates")
                .isEqualTo(200);
        assertThat(response.body().at("/booking/bookingdates/checkin").asText())
                .as("corrupt date persisted rather than rejected")
                .isEqualTo("0NaN-aN-aN");
    }

    @ApiTest
    @Story("Absent input validation")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("FINDING: a checkout date before the checkin date is accepted")
    void checkoutBeforeCheckinIsAccepted() {
        Map<String, Object> payload = validPayload();
        payload.put("bookingdates", Map.of("checkin", "2026-10-12", "checkout", "2026-10-05"));

        ApiResponse<JsonNode> response = bookings.createRaw(payload, token());

        assertThat(response.statusCode())
                .as("expected 400 Bad Request for an inverted date range")
                .isEqualTo(200);
        assertThat(response.body().at("/booking/bookingdates/checkout").asText())
                .isEqualTo("2026-10-05");
    }

    @ApiTest
    @Story("Absent input validation")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("FINDING: a negative totalprice is accepted and stored")
    void negativeTotalPriceIsAccepted() {
        Map<String, Object> payload = validPayload();
        payload.put("totalprice", -500);

        ApiResponse<JsonNode> response = bookings.createRaw(payload, token());

        assertThat(response.statusCode())
                .as("expected 400 Bad Request for a negative price")
                .isEqualTo(200);
        assertThat(response.body().at("/booking/totalprice").asInt()).isEqualTo(-500);
    }
}
