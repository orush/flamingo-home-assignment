package com.flamingo.qa.api.graphql;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Map;

/** One entry of a GraphQL {@code errors} array. */
@Value
@Builder
@Jacksonized
public class GraphQlError {
    String message;
    /** Field path the error belongs to; mixes names and list indices, hence Object. */
    List<Object> path;
    Map<String, Object> extensions;
}
