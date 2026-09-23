package com.flamingo.qa.api.graphql.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** A movie from the Hygraph video-streaming example schema. */
@Value
@Builder
@Jacksonized
public class Movie {
    String id;
    String title;
    Publisher publishedBy;
}
