/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;

class RefreshingOauthTokenTest {

    @Test
    void test_default_builder_uses_default_ssl_configuration() {
        AtomicReference<RefreshingOauthToken.SslConfiguration> sslConfiguration = new AtomicReference<>();
        RefreshingOauthToken refreshingOauthToken = new RefreshingOauthToken(
                RefreshingOauthToken.builder().hostname("example.com").scope(List.of("scope")),
                (_hostname, _scope, config) -> {
                    sslConfiguration.set(config);
                    return "token";
                });

        assertThat(refreshingOauthToken.getToken()).isEqualTo("token");
        assertThat(sslConfiguration.get()).isInstanceOf(RefreshingOauthToken.DefaultSslConfiguration.class);
    }

    @Test
    void test_builder_allows_overriding_ca_path() {
        AtomicReference<RefreshingOauthToken.SslConfiguration> sslConfiguration = new AtomicReference<>();
        RefreshingOauthToken refreshingOauthToken = new RefreshingOauthToken(
                RefreshingOauthToken.builder()
                        .hostname("example.com")
                        .scope(List.of("scope"))
                        .withCaPath("/custom/ca.pem"),
                (_hostname, _scope, config) -> {
                    sslConfiguration.set(config);
                    return "token";
                });

        assertThat(refreshingOauthToken.getToken()).isEqualTo("token");
        assertThat(sslConfiguration.get()).isInstanceOf(RefreshingOauthToken.CaPathSslConfiguration.class);
        assertThat(((RefreshingOauthToken.CaPathSslConfiguration) sslConfiguration.get()).caPath())
                .isEqualTo("/custom/ca.pem");
    }

    @Test
    void test_builder_allows_overriding_ssl_context() throws Exception {
        AtomicReference<RefreshingOauthToken.SslConfiguration> sslConfiguration = new AtomicReference<>();
        SSLContext sslContext = SSLContext.getDefault();
        RefreshingOauthToken refreshingOauthToken = new RefreshingOauthToken(
                RefreshingOauthToken.builder()
                        .hostname("example.com")
                        .scope(List.of("scope"))
                        .withSslContext(sslContext),
                (_hostname, _scope, config) -> {
                    sslConfiguration.set(config);
                    return "token";
                });

        assertThat(refreshingOauthToken.getToken()).isEqualTo("token");
        assertThat(sslConfiguration.get()).isInstanceOf(RefreshingOauthToken.ProvidedSslContext.class);
        assertThat(((RefreshingOauthToken.ProvidedSslContext) sslConfiguration.get()).sslContext())
                .isSameAs(sslContext);
    }

    @Test
    void test_token_is_cached_until_refresh_interval_has_elapsed() {
        AtomicInteger fetchCount = new AtomicInteger();
        RefreshingOauthToken refreshingOauthToken = new RefreshingOauthToken(
                RefreshingOauthToken.builder().hostname("example.com").scope(List.of("scope")),
                (_hostname, _scope, _config) -> "token-" + fetchCount.incrementAndGet());

        assertThat(refreshingOauthToken.getToken()).isEqualTo("token-1");
        assertThat(refreshingOauthToken.getToken()).isEqualTo("token-1");
        assertThat(fetchCount).hasValue(1);
    }

    @Test
    void test_token_is_refreshed_when_refresh_interval_has_elapsed() {
        AtomicInteger fetchCount = new AtomicInteger();
        RefreshingOauthToken refreshingOauthToken = new RefreshingOauthToken(
                RefreshingOauthToken.builder()
                        .hostname("example.com")
                        .scope(List.of("scope"))
                        .withRefreshInterval(Duration.ofMillis(-1)),
                (_hostname, _scope, _config) -> "token-" + fetchCount.incrementAndGet());

        assertThat(refreshingOauthToken.getToken()).isEqualTo("token-1");
        assertThat(refreshingOauthToken.getToken()).isEqualTo("token-2");
        assertThat(fetchCount).hasValue(2);
    }

    @Test
    void test_builder_requires_hostname() {
        assertThatThrownBy(() ->
                        RefreshingOauthToken.builder().scope(List.of("scope")).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("hostname is required");
    }

    @Test
    void test_builder_requires_scope() {
        assertThatThrownBy(() ->
                        RefreshingOauthToken.builder().hostname("example.com").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("scope is required");
    }
}
