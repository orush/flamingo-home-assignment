package com.flamingo.qa.api;

import com.flamingo.qa.config.Config;
import com.flamingo.qa.reporting.RedactingAllureFilter;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

/**
 * Builds the shared request specifications.
 *
 * <p>Specs are built on demand rather than held in static fields, so each one
 * resolves only the configuration it needs: a GraphQL-only run does not require
 * the Restful Booker settings to be present.
 */
public final class RestClientFactory {

    /**
     * Accept is set as a literal string, not {@code ContentType.JSON}.
     *
     * <p>REST Assured expands {@code ContentType.JSON} on Accept into
     * {@code application/json, application/javascript, text/javascript, text/json},
     * and Restful Booker answers 418 ("I'm a Teapot") to any Accept value other
     * than exactly {@code application/json}. That rejection looks nothing like a
     * content-negotiation problem, so it is worth stating plainly here.
     */
    private static final String ACCEPT_JSON = "application/json";


    private RestClientFactory() {
    }

    public static RequestSpecification booker() {
        return jsonSpec(Config.bookerBaseUrl(), Config.timeoutMillis());
    }

    public static RequestSpecification graphQl() {
        return jsonSpec(Config.graphQlEndpoint(), Config.timeoutMillis());
    }

    /** Public so framework self-tests can use a short timeout without touching global config. */
    public static RequestSpecification jsonSpec(String baseUri, int timeoutMillis) {
        return given().spec(new RequestSpecBuilder()
                .setConfig(config(timeoutMillis))
                .setBaseUri(baseUri)
                .setContentType(ContentType.JSON)
                .setAccept(ACCEPT_JSON)
                .addFilter(new RedactingAllureFilter())
                .build());
    }

    /**
     * Serialise request bodies with the project's own mapper, so the rules in
     * {@link Json} apply to what is sent as well as to what is read back; and
     * bound every connect and read. REST Assured sets no timeout by default, so
     * without one a request to a stalled service blocks the build indefinitely
     * rather than failing with a retryable {@code SocketTimeoutException}.
     */
    private static RestAssuredConfig config(int timeoutMillis) {
        return RestAssuredConfig.config()
                .objectMapperConfig(ObjectMapperConfig.objectMapperConfig()
                        .jackson2ObjectMapperFactory((type, charset) -> Json.mapper()))
                .httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", timeoutMillis)
                        .setParam("http.socket.timeout", timeoutMillis));
    }
}
