package com.flamingo.qa.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.flamingo.qa.api.booker.BookingClient;
import com.flamingo.qa.api.booker.TokenProvider;
import com.flamingo.qa.api.booker.model.Booking;
import com.flamingo.qa.config.Secret;
import com.flamingo.qa.data.TestDataFactory;
import com.flamingo.qa.junit.ApiTest;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * How the booking endpoints behave when given bad input.
 *
 * <p>Authorization is covered separately in {@code BookingAuthorizationTest};
 * nothing here duplicates it.
 *
 * <p><b>The tests in the final section are expected to fail.</b> They assert the
 * behaviour a correct API should have, so each failure is a live defect report
 * naming the input, the expected response and what the service did instead.
 * They are tagged {@code finding} so a pipeline can report them without gating
 * on them:
 *
 * <pre>
 *   ./mvnw test -Dgroups=api -DexcludedGroups=finding   # gating build
 *   ./mvnw test -Dgroups=finding                        # defect report
 * </pre>
 *
 * <p>Two of them are worse than a missing check: the service accepts the request
 * and writes corrupted data to its store.
 */
@Epic("Restful Booker")
@Feature("Booking error handling")
class BookingNegativeTest {

    private static final int ABSENT_BOOKING_ID = 99_999_999;

    private final BookingClient bookings = new BookingClient();

    private Secret token() {
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

    /** What the service stored at {@code pointer}, for use in failure messages. */
    private static String stored(ApiResponse<JsonNode> response, String pointer) {
        JsonNode body = response.body();
        return body == null ? "<no JSON body>" : body.at(pointer).toString();
    }

    // ---------------------------------------------------------------
    // Resources that do not exist
    // ---------------------------------------------------------------

    @ApiTest
    @Story("Unknown resources")
    @DisplayName("Retrieving a non-existent booking returns 404 with a plain-text body")
    void returnsNotFoundForUnknownId() {
        ApiResponse<Booking> response = bookings.getById(ABSENT_BOOKING_ID, token());

        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(404);
            softly.assertThat(response.rawBody()).isEqualTo("Not Found");
            softly.assertThat(response.body())
                    .as("no model is produced from a plain-text error body")
                    .isNull();
        });
    }

    @ApiTest
    @Story("Unknown resources")
    @DisplayName("A non-numeric booking id returns 404 rather than a parse error")
    void returnsNotFoundForNonNumericId() {
        ApiResponse<Booking> response = bookings.getByRawId("not-a-number", token());

        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(404);
            softly.assertThat(response.rawBody()).isEqualTo("Not Found");
        });
    }

    @ApiTest
    @Story("Unknown resources")
    @DisplayName("Updating a non-existent booking returns 405 Method Not Allowed, not 404")
    @Description("Surprising, but pinned deliberately: the resource is missing, so 404 would "
            + "be the expected answer. Asserting the real behaviour means a change to it is noticed.")
    void rejectsUpdateOfNonExistentBooking() {
        ApiResponse<JsonNode> response =
                bookings.updateRaw(ABSENT_BOOKING_ID, validPayload(), token());

        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(405);
            softly.assertThat(response.rawBody()).isEqualTo("Method Not Allowed");
        });
    }

    @ApiTest
    @Story("Unknown resources")
    @DisplayName("Partially updating a non-existent booking returns 405 Method Not Allowed")
    void rejectsPartialUpdateOfNonExistentBooking() {
        ApiResponse<Booking> response =
                bookings.patch(ABSENT_BOOKING_ID, Map.of("firstname", "Ghost"), token());

        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(405);
            softly.assertThat(response.rawBody()).isEqualTo("Method Not Allowed");
        });
    }

    @ApiTest
    @Story("Unknown resources")
    @DisplayName("Deleting a non-existent booking returns 405 Method Not Allowed")
    void rejectsDeleteOfNonExistentBooking() {
        ApiResponse<Void> response = bookings.delete(ABSENT_BOOKING_ID, token());

        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(405);
            softly.assertThat(response.rawBody()).isEqualTo("Method Not Allowed");
        });
    }

    // ---------------------------------------------------------------
    // Malformed requests, correctly rejected
    // ---------------------------------------------------------------

    @ApiTest
    @Story("Malformed requests")
    @DisplayName("Syntactically invalid JSON is rejected with 400")
    void rejectsMalformedJson() {
        ApiResponse<JsonNode> response = bookings.createRaw("{\"firstname\":", token());

        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(400);
            softly.assertThat(response.rawBody()).isEqualTo("Bad Request");
        });
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

        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(400);
            softly.assertThat(response.rawBody()).isEqualTo("Bad Request");
        });
    }

    // ===============================================================
    // FINDINGS — these assert correct behaviour and therefore FAIL.
    // Each failure is a defect report against the service.
    // ===============================================================

    @ApiTest
    @Tag("finding")
    @Story("Absent input validation")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("FINDING: an empty create body must be rejected with 400, not 500")
    @Description("A client mistake is reported as a server fault, which hides the cause from "
            + "the caller and pollutes server error metrics.")
    void emptyCreateBodyMustBeRejected() {
        ApiResponse<JsonNode> response = bookings.createRaw(Map.of(), token());

        assertThat(response.statusCode())
                .as("POST /booking with an empty body {} must answer 400 Bad Request; "
                        + "the service answered %d (%s)", response.statusCode(), response.rawBody())
                .isEqualTo(400);
    }

    @ApiTest
    @Tag("finding")
    @Story("Absent input validation")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("FINDING: a create missing a required field must be rejected with 400, not 500")
    void missingRequiredFieldMustBeRejected() {
        Map<String, Object> payload = validPayload();
        payload.remove("lastname");

        ApiResponse<JsonNode> response = bookings.createRaw(payload, token());

        assertThat(response.statusCode())
                .as("POST /booking without 'lastname' must answer 400 Bad Request; "
                        + "the service answered %d (%s)", response.statusCode(), response.rawBody())
                .isEqualTo(400);
    }

    @ApiTest
    @Tag("finding")
    @Story("Absent input validation")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("FINDING: a non-numeric totalprice must be rejected, not silently discarded")
    @Description("The value is neither rejected nor preserved. It is dropped without telling "
            + "the caller, so the booking ends up with no price at all.")
    void nonNumericTotalPriceMustBeRejected() {
        Map<String, Object> payload = validPayload();
        payload.put("totalprice", "free");

        ApiResponse<JsonNode> response = bookings.createRaw(payload, token());

        assertThat(response.statusCode())
                .as("POST /booking with totalprice=\"free\" must answer 400 Bad Request; "
                        + "the service answered %d and stored totalprice as %s",
                        response.statusCode(), stored(response, "/booking/totalprice"))
                .isEqualTo(400);
    }

    @ApiTest
    @Tag("finding")
    @Story("Absent input validation")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("FINDING: unparseable dates must be rejected, not persisted as \"0NaN-aN-aN\"")
    @Description("The worst of the validation gaps: the request succeeds and corrupt data is "
            + "written to the store, where every later reader must cope with it.")
    void unparseableDatesMustBeRejected() {
        Map<String, Object> payload = validPayload();
        payload.put("bookingdates", Map.of("checkin", "not-a-date", "checkout", "also-not-a-date"));

        ApiResponse<JsonNode> response = bookings.createRaw(payload, token());

        assertThat(response.statusCode())
                .as("POST /booking with checkin=\"not-a-date\" must answer 400 Bad Request; "
                        + "the service answered %d and PERSISTED checkin as %s",
                        response.statusCode(), stored(response, "/booking/bookingdates/checkin"))
                .isEqualTo(400);
    }

    @ApiTest
    @Tag("finding")
    @Story("Absent input validation")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("FINDING: a checkout date before the checkin date must be rejected")
    void checkoutBeforeCheckinMustBeRejected() {
        Map<String, Object> payload = validPayload();
        payload.put("bookingdates", Map.of("checkin", "2026-10-12", "checkout", "2026-10-05"));

        ApiResponse<JsonNode> response = bookings.createRaw(payload, token());

        assertThat(response.statusCode())
                .as("POST /booking with checkout (2026-10-05) before checkin (2026-10-12) must "
                        + "answer 400 Bad Request; the service answered %d and stored the range as-is",
                        response.statusCode())
                .isEqualTo(400);
    }

    @ApiTest
    @Tag("finding")
    @Story("Absent input validation")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("FINDING: a negative totalprice must be rejected")
    void negativeTotalPriceMustBeRejected() {
        Map<String, Object> payload = validPayload();
        payload.put("totalprice", -500);

        ApiResponse<JsonNode> response = bookings.createRaw(payload, token());

        assertThat(response.statusCode())
                .as("POST /booking with totalprice=-500 must answer 400 Bad Request; "
                        + "the service answered %d and stored totalprice as %s",
                        response.statusCode(), stored(response, "/booking/totalprice"))
                .isEqualTo(400);
    }
}
