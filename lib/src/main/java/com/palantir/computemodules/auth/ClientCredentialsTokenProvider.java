/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.computemodules.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public final class ClientCredentialsTokenProvider implements Supplier<String> {
    private static final SafeLogger log = SafeLoggerFactory.get(ClientCredentialsTokenProvider.class);
    private static final Duration refreshInterval = Duration.ofHours(1);
    private static final String oauthTokenPath = "/multipass/api/oauth2/token";
    private static final ObjectMapper mapper = new ObjectMapper();

    private final HttpClient client;
    private Instant lastRefreshed;
    private Optional<AuthTokenResponse> maybeTokenResponse;

    private final String hostname;
    private final String clientId;
    private final String clientSecret;
    private final List<String> scopes;

    private ClientCredentialsTokenProvider(
            String hostname, String clientId, String clientSecret, List<String> scopes, HttpClient client) {
        this.hostname = extractHost(hostname);
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scopes = scopes;
        this.client = client;
        lastRefreshed = Instant.now();
        maybeTokenResponse = Optional.empty();
    }

    public static ClientCredentialsTokenProviderBuilder builder() {
        return new ClientCredentialsTokenProviderBuilder();
    }

    @Override
    public synchronized String get() {
        if (shouldRefreshToken()) {
            refreshToken();
        }
        return maybeTokenResponse.map(AuthTokenResponse::accessToken).orElseThrow();
    }

    public static Optional<String> getClientId() {
        String clientId = System.getenv("CLIENT_ID");
        return Optional.ofNullable(clientId);
    }

    public static Optional<String> getClientSecret() {
        String clientSecret = System.getenv("CLIENT_SECRET");
        return Optional.ofNullable(clientSecret);
    }

    private static String extractHost(String host) {
        try {
            URL url = new URL(host);
            return url.getHost();
        } catch (MalformedURLException e) {
            return host;
        }
    }

    private boolean shouldRefreshToken() {
        if (maybeTokenResponse.isEmpty()) {
            return true;
        }
        Instant now = Instant.now();
        Duration timeSinceLastRefresh = Duration.between(lastRefreshed, now);
        return timeSinceLastRefresh.compareTo(refreshInterval) > 0;
    }

    private String buildFormParams() {
        StringJoiner joiner = new StringJoiner("&");
        joiner.add("grant_type=" + URLEncoder.encode("client_credentials", StandardCharsets.UTF_8));
        joiner.add("client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8));
        joiner.add("client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8));
        joiner.add("scope=" + URLEncoder.encode(String.join(" ", scopes), StandardCharsets.UTF_8));

        return joiner.toString();
    }

    private void refreshToken() {
        // TODO(sk): might want to retry
        try {
            lastRefreshed = Instant.now();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("https", hostname, oauthTokenPath, null))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(buildFormParams()))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                maybeTokenResponse = deserialize(response.body());
                return;
            }
            log.warn("Non-200 status code returned from token endpoint", SafeArg.of("response", response));
        } catch (URISyntaxException | IOException | InterruptedException e) {
            log.error("Exception raised trying to refresh token", e);
        }
    }

    private Optional<AuthTokenResponse> deserialize(String raw) {
        try {
            AuthTokenResponse response = mapper.readValue(raw, AuthTokenResponse.class);
            return Optional.of(response);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static final class ClientCredentialsTokenProviderBuilder {
        private @Nullable String hostname = null;
        private @Nullable String clientId;
        private @Nullable String clientSecret;
        private final List<String> scopes;
        private HttpClient client = HttpClient.newBuilder().build();

        private ClientCredentialsTokenProviderBuilder() {
            clientId = getClientId().orElse(null);
            clientSecret = getClientSecret().orElse(null);
            scopes = new ArrayList<>();
        }

        public ClientCredentialsTokenProviderBuilder hostname(String value) {
            hostname = value;
            return this;
        }

        public ClientCredentialsTokenProviderBuilder clientId(String value) {
            clientId = value;
            return this;
        }

        public ClientCredentialsTokenProviderBuilder clientSecret(String value) {
            clientSecret = value;
            return this;
        }

        public ClientCredentialsTokenProviderBuilder scopes(String... values) {
            Collections.addAll(scopes, values);
            return this;
        }

        public ClientCredentialsTokenProviderBuilder client(HttpClient value) {
            client = value;
            return this;
        }

        public ClientCredentialsTokenProvider build() {
            if (hostname == null) {
                throw new SafeIllegalArgumentException("hostname must be set");
            }
            if (clientId == null) {
                throw new SafeIllegalArgumentException("clientId must be set");
            }
            if (clientSecret == null) {
                throw new SafeIllegalArgumentException("clientSecret must be set");
            }
            return new ClientCredentialsTokenProvider(hostname, clientId, clientSecret, scopes, client);
        }
    }
}
