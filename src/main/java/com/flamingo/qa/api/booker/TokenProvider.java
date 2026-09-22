package com.flamingo.qa.api.booker;

import com.flamingo.qa.api.ApiResponse;
import com.flamingo.qa.api.booker.model.AuthResponse;
import com.flamingo.qa.config.Config;

/**
 * Authenticates once per JVM and caches the token.
 *
 * <p>The assignment asks that these public services not be overloaded, so the
 * whole suite spends a single /auth call rather than one per test.
 */
public final class TokenProvider {

    private static volatile String cachedToken;

    private TokenProvider() {
    }

    public static String token() {
        String local = cachedToken;
        if (local == null) {
            synchronized (TokenProvider.class) {
                local = cachedToken;
                if (local == null) {
                    ApiResponse<AuthResponse> response = new AuthClient()
                            .createToken(Config.bookerUsername(), Config.bookerPassword());
                    if (response.statusCode() != 200 || response.body().getToken() == null) {
                        throw new IllegalStateException(
                                "Could not obtain auth token: " + response.rawBody());
                    }
                    local = response.body().getToken();
                    cachedToken = local;
                }
            }
        }
        return local;
    }
}
