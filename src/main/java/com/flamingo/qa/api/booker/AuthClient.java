package com.flamingo.qa.api.booker;

import com.flamingo.qa.api.ApiResponse;
import com.flamingo.qa.api.RestClientFactory;
import com.flamingo.qa.api.booker.model.AuthRequest;
import com.flamingo.qa.api.booker.model.AuthResponse;
import io.qameta.allure.Step;
import io.restassured.response.Response;

public class AuthClient {

    @Step("Request auth token for user {username}")
    public ApiResponse<AuthResponse> createToken(String username, String password) {
        Response response = RestClientFactory.booker()
                .body(AuthRequest.builder().username(username).password(password).build())
                .post("/auth");
        return ApiResponse.from(response, AuthResponse.class);
    }
}
