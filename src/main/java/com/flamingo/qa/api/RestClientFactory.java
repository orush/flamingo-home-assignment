package com.flamingo.qa.api;

import com.flamingo.qa.config.Config;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

/** Builds the shared request specifications. */
public final class RestClientFactory {

    static {
        // Quiet on green, fully diagnosable on red.
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails(LogDetail.ALL);
    }

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

    private static final RequestSpecification BOOKER_SPEC = new RequestSpecBuilder()
            .setBaseUri(Config.bookerBaseUrl())
            .setContentType(ContentType.JSON)
            .setAccept(ACCEPT_JSON)
            .addFilter(new AllureRestAssured())
            .build();

    private RestClientFactory() {
    }

    public static RequestSpecification booker() {
        return given().spec(BOOKER_SPEC);
    }
}
