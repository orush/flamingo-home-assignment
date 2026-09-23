package com.flamingo.qa.api;

import com.flamingo.qa.config.Config;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
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

    /**
     * Serialise request bodies with the project's own mapper, so the rules in
     * {@link Json} apply to what is sent as well as to what is read back.
     * Without this, REST Assured builds a private mapper of its own.
     */
    private static final RestAssuredConfig CONFIG = RestAssuredConfig.config()
            .objectMapperConfig(ObjectMapperConfig.objectMapperConfig()
                    .jackson2ObjectMapperFactory((type, charset) -> Json.mapper()));

    private RestClientFactory() {
    }

    public static RequestSpecification booker() {
        return jsonSpec(Config.bookerBaseUrl());
    }

    public static RequestSpecification graphQl() {
        return jsonSpec(Config.graphQlEndpoint());
    }

    private static RequestSpecification jsonSpec(String baseUri) {
        return given().spec(new RequestSpecBuilder()
                .setConfig(CONFIG)
                .setBaseUri(baseUri)
                .setContentType(ContentType.JSON)
                .setAccept(ACCEPT_JSON)
                .addFilter(new AllureRestAssured())
                .build());
    }
}
