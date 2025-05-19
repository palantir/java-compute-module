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

import java.util.Optional;

public class ClientCredentials {
    private final String clientId;
    private final String clientSecret;

    public final class ThirdPartyCredentials {
        private final String clientId;
        private final String clientSecret;

        private ThirdPartyCredentials(Builder builder) {
            this.clientId = builder.clientId;
            this.clientSecret = builder.clientSecret;
        }

        public Optional<String> getClientId() {
            return Optional.ofNullable(clientId);
        }

        public Optional<String> getClientSecret() {
            return Optional.ofNullable(clientSecret);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String clientId;
            private String clientSecret;

            private Builder() {}

            public Builder clientId(String clientId) {
                this.clientId = clientId;
                return this;
            }

            public Builder clientSecret(String clientSecret) {
                this.clientSecret = clientSecret;
                return this;
            }

            public ThirdPartyCredentials build() {
                return new ThirdPartyCredentials(this);
            }
        }
}
