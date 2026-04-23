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

import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.net.ssl.SSLContext;

public final class RefreshingOauthToken {

    private static final Duration DEFAULT_REFRESH_INTERVAL = Duration.ofMinutes(30);
    private static final TokenFetcher DEFAULT_TOKEN_FETCHER = (hostname, scope, sslConfiguration) -> {
        if (sslConfiguration instanceof CaPathSslConfiguration caPathSslConfiguration) {
            return ThirdPartyAuth.fetchOAuthTokenWithCaPath(hostname, scope, caPathSslConfiguration.caPath());
        }
        if (sslConfiguration instanceof ProvidedSslContext providedSslContext) {
            return ThirdPartyAuth.fetchOAuthTokenWithSslContext(hostname, scope, providedSslContext.sslContext());
        }
        return ThirdPartyAuth.fetchOAuthToken(hostname, scope);
    };

    private final String hostname;
    private final List<String> scope;
    private final Duration refreshInterval;
    private final SslConfiguration sslConfiguration;
    private final TokenFetcher tokenFetcher;

    private String token = "";
    private volatile Instant lastRefreshTime = Instant.EPOCH;

    public RefreshingOauthToken(String hostname, List<String> scope, Duration refreshInterval) {
        this(builder().hostname(hostname).scope(scope).withRefreshInterval(refreshInterval));
    }

    public RefreshingOauthToken(String hostname, List<String> scope) {
        this(hostname, scope, DEFAULT_REFRESH_INTERVAL);
    }

    private RefreshingOauthToken(Builder builder) {
        this.hostname = builder.hostname.orElseThrow(() -> new IllegalStateException("hostname is required"));
        this.scope = builder.scope.orElseThrow(() -> new IllegalStateException("scope is required"));
        this.refreshInterval = builder.refreshInterval;
        this.sslConfiguration = builder.sslConfiguration;
        this.tokenFetcher = builder.tokenFetcher;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getToken() {
        if (this.token.isEmpty()
                || Duration.between(lastRefreshTime, Instant.now()).compareTo(refreshInterval) > 0) {
            String newToken = fetchToken();
            if (newToken.isEmpty()) {
                throw new SafeRuntimeException("Failed to refresh token");
            }

            this.token = newToken;
            lastRefreshTime = Instant.now();
        }
        return this.token;
    }

    private String fetchToken() {
        return tokenFetcher.fetch(this.hostname, this.scope, sslConfiguration);
    }

    @FunctionalInterface
    interface TokenFetcher {
        String fetch(String hostname, List<String> scope, SslConfiguration sslConfiguration);
    }

    sealed interface SslConfiguration permits DefaultSslConfiguration, CaPathSslConfiguration, ProvidedSslContext {}

    static final class DefaultSslConfiguration implements SslConfiguration {
        private static final DefaultSslConfiguration INSTANCE = new DefaultSslConfiguration();

        private DefaultSslConfiguration() {}
    }

    record CaPathSslConfiguration(String caPath) implements SslConfiguration {
        CaPathSslConfiguration {
            Objects.requireNonNull(caPath, "caPath");
        }
    }

    record ProvidedSslContext(SSLContext sslContext) implements SslConfiguration {
        ProvidedSslContext {
            Objects.requireNonNull(sslContext, "sslContext");
        }
    }

    public static final class Builder {
        private Optional<String> hostname = Optional.empty();
        private Optional<List<String>> scope = Optional.empty();
        private Duration refreshInterval = DEFAULT_REFRESH_INTERVAL;
        private SslConfiguration sslConfiguration = DefaultSslConfiguration.INSTANCE;
        private TokenFetcher tokenFetcher = DEFAULT_TOKEN_FETCHER;

        private Builder() {}

        public Builder hostname(String newHostname) {
            this.hostname = Optional.of(Objects.requireNonNull(newHostname, "hostname"));
            return this;
        }

        public Builder scope(List<String> newScope) {
            this.scope = Optional.of(List.copyOf(Objects.requireNonNull(newScope, "scope")));
            return this;
        }

        public Builder withRefreshInterval(Duration newRefreshInterval) {
            this.refreshInterval = Objects.requireNonNull(newRefreshInterval, "refreshInterval");
            return this;
        }

        public Builder withCaPath(String caPath) {
            this.sslConfiguration = new CaPathSslConfiguration(caPath);
            return this;
        }

        public Builder withSslContext(SSLContext sslContext) {
            this.sslConfiguration = new ProvidedSslContext(sslContext);
            return this;
        }

        Builder withTokenFetcher(TokenFetcher newTokenFetcher) {
            this.tokenFetcher = Objects.requireNonNull(newTokenFetcher, "tokenFetcher");
            return this;
        }

        public RefreshingOauthToken build() {
            return new RefreshingOauthToken(this);
        }
    }
}
