package com.flamingo.qa.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.restassured.response.Response;

/**
 * One return type for both happy and error paths.
 *
 * <p>Restful Booker answers errors with plain text ({@code Not Found},
 * {@code Forbidden}), so {@link #body()} is null whenever the response was not
 * JSON. Negative tests assert on {@link #rawBody()}.
 */
public final class ApiResponse<T> {

    private final int statusCode;
    private final T body;
    private final String rawBody;

    private ApiResponse(int statusCode, T body, String rawBody) {
        this.statusCode = statusCode;
        this.body = body;
        this.rawBody = rawBody;
    }

    public static <T> ApiResponse<T> from(Response response, Class<T> type) {
        String raw = response.asString();
        T parsed = null;
        if (isJson(response) && !raw.isBlank()) {
            try {
                parsed = Json.mapper().readValue(raw, type);
            } catch (JsonProcessingException e) {
                // A JSON response that will not map to its model is a real defect,
                // not something to swallow.
                throw new IllegalStateException(
                        "Could not map JSON response to " + type.getSimpleName() + ": " + raw, e);
            }
        }
        return new ApiResponse<>(response.statusCode(), parsed, raw);
    }

    private static boolean isJson(Response response) {
        String contentType = response.getContentType();
        return contentType != null && contentType.toLowerCase().contains("json");
    }

    public int statusCode() {
        return statusCode;
    }

    /** Mapped body, or null when the response was not JSON. */
    public T body() {
        return body;
    }

    public String rawBody() {
        return rawBody;
    }
}
