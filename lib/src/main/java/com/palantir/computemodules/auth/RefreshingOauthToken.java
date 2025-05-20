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

import com.palantir.logsafe.Unsafe;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class RefreshingOauthToken {

    private static final Duration DEFAULT_REFRESH_INTERVAL = Duration.ofMinutes(30);
    private static final SafeLogger log = SafeLoggerFactory.get(RefreshingOauthToken.class);

    private final String hostname;
    private final List<String> scope;
    private final Duration refreshInterval;

    private final AtomicReference<String> token = new AtomicReference<>(null);
    private volatile Instant lastRefreshTime = Instant.EPOCH;

    public RefreshingOauthToken(String hostname, List<String> scope, Duration refreshInterval) {
        this.hostname = hostname;
        this.scope = scope;
        this.refreshInterval = refreshInterval;
    }

    public RefreshingOauthToken(String hostname, List<String> scope) {
        this(hostname, scope, DEFAULT_REFRESH_INTERVAL);
    }

    public Optional<String> getToken() {
        String currentToken = token.get();
        if (currentToken == null
                || Duration.between(lastRefreshTime, Instant.now()).compareTo(refreshInterval) > 0) {
            Optional<String> newToken = fetchToken();
            if (newToken.isEmpty()) {
                log.error("Unable to refresh token.");
                return Optional.empty();
            }

            token.set(newToken.get());
            lastRefreshTime = Instant.now();
        }
        return Optional.ofNullable(token.get());
    }

    @Unsafe
    private Optional<String> fetchToken() {
        return ThirdPartyAuth.fetchOAuthToken(hostname, scope);
    }
}
