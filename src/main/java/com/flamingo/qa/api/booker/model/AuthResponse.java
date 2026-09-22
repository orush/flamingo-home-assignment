package com.flamingo.qa.api.booker.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Carries either a token (success) or a reason (failure). Never both. */
@Value
@Builder
@Jacksonized
public class AuthResponse {
    String token;
    String reason;
}
