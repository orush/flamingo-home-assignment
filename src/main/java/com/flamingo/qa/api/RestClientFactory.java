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

    private static final RequestSpecification BOOKER_SPEC = new RequestSpecBuilder()
            .setBaseUri(Config.bookerBaseUrl())
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .addFilter(new AllureRestAssured())
            .build();

    private RestClientFactory() {
    }

    public static RequestSpecification booker() {
        return given().spec(BOOKER_SPEC);
    }
}
