package com.flamingo.qa.api;

import com.flamingo.qa.api.booker.AuthClient;
import com.flamingo.qa.api.booker.model.AuthResponse;
import com.flamingo.qa.config.Config;
import com.flamingo.qa.junit.ApiTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Restful Booker")
@Feature("Authentication")
class BookerAuthTest {

    private final AuthClient authClient = new AuthClient();

    @ApiTest
    @DisplayName("Valid credentials return an auth token")
    void returnsTokenForValidCredentials() {
        ApiResponse<AuthResponse> response =
                authClient.createToken(Config.bookerUsername(), Config.bookerPassword());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().getToken()).isNotBlank();
        assertThat(response.body().getReason()).isNull();
    }

    @ApiTest
    @DisplayName("Bad password returns HTTP 200 with a 'Bad credentials' reason, not 401")
    void rejectsBadPasswordWithReasonNotUnauthorized() {
        ApiResponse<AuthResponse> response =
                authClient.createToken(Config.bookerUsername(), "definitely-wrong");

        // Documented quirk of this API: the failure is reported in the body,
        // not the status line.
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().getReason()).isEqualTo("Bad credentials");
        assertThat(response.body().getToken()).isNull();
    }
}
