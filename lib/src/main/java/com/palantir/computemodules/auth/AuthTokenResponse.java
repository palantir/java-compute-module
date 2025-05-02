package com.palantir.computemodules.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("scope") String scope,
        @JsonProperty("expires_in") Integer expiresIn,
        @JsonProperty("token_type") String tokenType) {}
