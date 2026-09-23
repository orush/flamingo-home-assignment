package com.flamingo.qa.api.booker;

import com.flamingo.qa.api.ApiResponse;
import com.flamingo.qa.api.RestClientFactory;
import com.flamingo.qa.api.booker.model.AuthRequest;
import com.flamingo.qa.api.booker.model.AuthResponse;
import com.flamingo.qa.config.Secret;
import io.qameta.allure.Step;
import io.restassured.response.Response;

public class AuthClient {

    @Step("Request auth token")
    public ApiResponse<AuthResponse> createToken(Secret username, Secret password) {
        Response response = RestClientFactory.booker()
                .body(AuthRequest.builder().username(username.reveal()).password(password.reveal()).build())
                .post("/auth");
        return ApiResponse.from(response, AuthResponse.class);
    }
}
