package com.flamingo.qa.api.graphql;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * A GraphQL request body.
 *
 * <p>Variables always travel as a separate map. Nothing in this framework
 * splices values into query text.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GraphQlRequest {
    String query;
    Map<String, Object> variables;
    String operationName;

    public static GraphQlRequest of(String query) {
        return builder().query(query).build();
    }

    public static GraphQlRequest of(String query, Map<String, Object> variables) {
        return builder().query(query).variables(variables).build();
    }

    public static GraphQlRequest fromFile(String fileName, Map<String, Object> variables) {
        return of(QueryLoader.load(fileName), variables);
    }
}
