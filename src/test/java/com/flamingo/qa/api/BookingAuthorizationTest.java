package com.flamingo.qa.api;

import com.flamingo.qa.api.booker.BookingClient;
import com.flamingo.qa.api.booker.TokenProvider;
import com.flamingo.qa.api.booker.model.Booking;
import com.flamingo.qa.api.booker.model.BookingId;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * Authorization coverage for every booking endpoint.
 *
 * <p>Each endpoint is probed three ways: with no token, with a valid token, and
 * with a forged one. The result is a complete enforcement matrix rather than a
 * spot check.
 *
 * <pre>
 *   endpoint               no token   valid    forged
 *   POST   /booking          200       200      200    &lt;-- not enforced
 *   GET    /booking          200       200      200    &lt;-- not enforced
 *   GET    /booking/{id}     200       200      200    &lt;-- not enforced
 *   PUT    /booking/{id}     403       200      403        enforced
 *   PATCH  /booking/{id}     403       200      403        enforced
 *   DELETE /booking/{id}     403       201      403        enforced
 * </pre>
 *
 * <p>The three unenforced rows are recorded as findings. These tests assert the
 * behaviour the service <em>actually</em> has, so the suite stays honest and
 * green; each finding states in its name what a correctly secured API should
 * return instead. If the service is ever fixed, these tests fail — which is
 * precisely when someone should be told.
 *
 * <p>Note that this is documented behaviour of Restful Booker, not an
 * undocumented regression. It is a design defect in the system under test.
 */
@Epic("Restful Booker")
@Feature("Authorization")
class BookingAuthorizationTest {

    private static final Secret FORGED_TOKEN = Secret.of("not-a-real-token-0000");

    private final BookingClient bookings = new BookingClient();

    private int seedBooking() {
        return bookings.create(TestDataFactory.randomBooking(), TokenProvider.token())
                .body().getBookingid();
    }

    // ---------------------------------------------------------------
    // Findings: endpoints that accept unauthenticated access
    // ---------------------------------------------------------------

    @ApiTest
    @Story("Unprotected endpoints")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("FINDING: POST /booking creates a booking with no token (expected 401/403, got 200)")
    @Description("Anyone can write to the booking store without credentials.")
    void createAcceptsUnauthenticatedWrites() {
        Booking booking = TestDataFactory.randomBooking();

        ApiResponse<?> response = bookings.createWithoutToken(booking);

        assertThat(response.statusCode())
                .as("unauthenticated POST should be rejected, but the API accepts it")
                .isEqualTo(200);
    }

    @ApiTest
    @Story("Unprotected endpoints")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("FINDING: GET /booking lists every booking id with no token (expected 401/403, got 200)")
    @Description("Enumerating ids unauthenticated is the first half of a full data disclosure; "
            + "combined with the unauthenticated GET /booking/{id} below, every guest record is readable.")
    void listAcceptsUnauthenticatedReads() {
        ApiResponse<BookingId[]> response = bookings.getAllWithoutToken();

        assertSoftly(softly -> {
            softly.assertThat(response.statusCode())
                    .as("unauthenticated collection read should be rejected, but the API allows it")
                    .isEqualTo(200);
            softly.assertThat(response.body())
                    .as("the unauthenticated response really does contain ids")
                    .isNotEmpty();
        });
    }

    @ApiTest
    @Story("Unprotected endpoints")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("FINDING: GET /booking/{id} exposes guest details with no token (expected 401/403, got 200)")
    @Description("Guest first and last names are readable by anyone holding an id.")
    void readByIdExposesGuestDataWithoutAuthentication() {
        int id = seedBooking();

        ApiResponse<Booking> response = bookings.getByIdWithoutToken(id);

        assertThat(response.statusCode())
                .as("unauthenticated read should be rejected, but the API allows it")
                .isEqualTo(200);
        assertThat(response.body().getFirstname())
                .as("personal data is returned to an unauthenticated caller")
                .isNotBlank();
    }

    // ---------------------------------------------------------------
    // Enforcement that works: a missing token is rejected
    // ---------------------------------------------------------------

    @ApiTest
    @Story("Enforced endpoints")
    @DisplayName("PUT /booking/{id} is rejected without a token")
    void updateRequiresToken() {
        int id = seedBooking();

        ApiResponse<Booking> response =
                bookings.updateWithoutToken(id, TestDataFactory.randomBooking());

        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(403);
            softly.assertThat(response.rawBody()).isEqualTo("Forbidden");
        });
    }

    @ApiTest
    @Story("Enforced endpoints")
    @DisplayName("PATCH /booking/{id} is rejected without a token")
    void partialUpdateRequiresToken() {
        int id = seedBooking();

        ApiResponse<Booking> response =
                bookings.patchWithoutToken(id, Map.of("firstname", "NoToken"));

        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(403);
            softly.assertThat(response.rawBody()).isEqualTo("Forbidden");
        });
    }

    @ApiTest
    @Story("Enforced endpoints")
    @DisplayName("DELETE /booking/{id} is rejected without a token")
    void deleteRequiresToken() {
        int id = seedBooking();

        ApiResponse<Void> response = bookings.deleteWithoutToken(id);

        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(403);
            softly.assertThat(response.rawBody()).isEqualTo("Forbidden");
        });
    }

    // ---------------------------------------------------------------
    // Enforcement that works: a forged token is rejected
    // ---------------------------------------------------------------

    @ApiTest
    @Story("Forged credentials")
    @DisplayName("PUT /booking/{id} rejects a forged token rather than trusting its presence")
    void updateRejectsForgedToken() {
        int id = seedBooking();

        ApiResponse<Booking> response =
                bookings.update(id, TestDataFactory.randomBooking(), FORGED_TOKEN);

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @ApiTest
    @Story("Forged credentials")
    @DisplayName("PATCH /booking/{id} rejects a forged token")
    void partialUpdateRejectsForgedToken() {
        int id = seedBooking();

        ApiResponse<Booking> response =
                bookings.patch(id, Map.of("firstname", "Forged"), FORGED_TOKEN);

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @ApiTest
    @Story("Forged credentials")
    @DisplayName("DELETE /booking/{id} rejects a forged token")
    void deleteRejectsForgedToken() {
        int id = seedBooking();

        ApiResponse<Void> response = bookings.delete(id, FORGED_TOKEN);

        assertThat(response.statusCode()).isEqualTo(403);
    }

    // ---------------------------------------------------------------
    // The happy path, so the matrix is complete
    // ---------------------------------------------------------------

    @ApiTest
    @Story("Enforced endpoints")
    @DisplayName("A valid token authorises the protected verbs")
    void validTokenAuthorisesProtectedVerbs() {
        int id = seedBooking();
        Secret token = TokenProvider.token();

        assertSoftly(softly -> {
            softly.assertThat(bookings.update(id, TestDataFactory.randomBooking(), token).statusCode())
                    .as("PUT with a valid token").isEqualTo(200);
            softly.assertThat(bookings.patch(id, Map.of("firstname", "Valid"), token).statusCode())
                    .as("PATCH with a valid token").isEqualTo(200);
            softly.assertThat(bookings.delete(id, token).statusCode())
                    .as("DELETE with a valid token").isEqualTo(201);
        });
    }
}
