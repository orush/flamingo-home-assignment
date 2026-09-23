package com.flamingo.qa.api.graphql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.flamingo.qa.api.Json;
import com.flamingo.qa.api.RestClientFactory;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import io.restassured.response.Response;

public class GraphQlClient {

    @Step("Execute GraphQL request")
    public GraphQlResponse execute(GraphQlRequest request) {
        attachToReport(request);
        Response response = RestClientFactory.graphQl().body(request).post();
        return GraphQlResponse.from(response);
    }

    /**
     * Attaches the query as readable text. The HTTP attachment from the REST
     * Assured filter holds the same query JSON-escaped onto one line, which is
     * hard to read for anything longer than a few fields.
     */
    private void attachToReport(GraphQlRequest request) {
        Allure.addAttachment("GraphQL query", "text/plain", request.getQuery(), ".graphql");
        if (request.getVariables() != null) {
            Allure.addAttachment("GraphQL variables", "application/json",
                    toJson(request.getVariables()), ".json");
        }
    }

    private static String toJson(Object value) {
        try {
            return Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise GraphQL variables", e);
        }
    }
}
