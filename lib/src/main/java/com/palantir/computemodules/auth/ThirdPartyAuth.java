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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palantir.computemodules.ssl.SslUtils;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.logsafe.exceptions.SafeUncheckedIoException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLContext;

public final class ThirdPartyAuth {
    private static final String HTTPS = "https://";
    private static final String OAUTH_TOKEN_ENDPOINT = "/multipass/api/oauth2/token";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ThirdPartyAuth() {}

    public static ThirdPartyCredentials retrieveThirdPartyIdAndCreds() {
        String clientId = System.getenv("CLIENT_ID");
        String clientSecret = System.getenv("CLIENT_SECRET");
        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            throw new SafeRuntimeException(
                    "One or both of required environment variables not found: CLIENT_ID, CLIENT_SECRET. "
                            + "Make sure that your CM has Application permissions enabled.");
        }
        return new ThirdPartyCredentials(clientId, clientSecret);
    }

    public static String fetchOAuthToken(String hostname, List<String> scope) {
        ThirdPartyCredentials credentials = retrieveThirdPartyIdAndCreds();
        try {
            String formData = "grant_type=" + URLEncoder.encode("client_credentials", StandardCharsets.UTF_8)
                    + "&client_id=" + URLEncoder.encode(credentials.clientId(), StandardCharsets.UTF_8)
                    + "&client_secret="
                    + URLEncoder.encode(credentials.clientSecret(), StandardCharsets.UTF_8)
                    + "&scope=" + URLEncoder.encode(String.join(" ", scope), StandardCharsets.UTF_8);

            String url = HTTPS + hostname + OAUTH_TOKEN_ENDPOINT;

            SSLContext sslContext = SslUtils.createSslContext(System.getenv("DEFAULT_CA_PATH"));

            HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> tokenData =
                        OBJECT_MAPPER.readValue(response.body(), new TypeReference<Map<String, Object>>() {});

                if (tokenData != null && tokenData.containsKey("access_token")) {
                    return tokenData.get("access_token").toString();
                }
            }
        } catch (InterruptedException e) {
            throw new SafeRuntimeException("Interrupted while fetching OAuth token", e);
        } catch (IOException e) {
            throw new SafeUncheckedIoException("IOException while fetching OAuth token", e);
        }
        return "";
    }
}
