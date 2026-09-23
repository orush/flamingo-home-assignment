package com.flamingo.qa.api.graphql.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * The account that published a movie. Maps Hygraph's system {@code User} type,
 * named for the role it plays here rather than the generic type name.
 */
@Value
@Builder
@Jacksonized
public class Publisher {
    String id;
    String name;
}
