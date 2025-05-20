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
import com.palantir.logsafe.Unsafe;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public final class ThirdPartyAuth {

    private static final SafeLogger log = SafeLoggerFactory.get(ThirdPartyAuth.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ThirdPartyAuth() {}

    public static ThirdPartyCredentials retrieveThirdPartyIdAndCreds() {
        return new ThirdPartyCredentials(System.getenv("CLIENT_ID"), System.getenv("CLIENT_SECRET"));
    }

    @Unsafe
    public static Optional<String> fetchOAuthToken(String hostname, List<String> scope) {
        ThirdPartyCredentials credentials = retrieveThirdPartyIdAndCreds();
        try {
            String formData = "grant_type=" + URLEncoder.encode("client_credentials", StandardCharsets.UTF_8)
                    + "&client_id=" + URLEncoder.encode(credentials.clientId().orElseThrow(), StandardCharsets.UTF_8)
                    + "&client_secret="
                    + URLEncoder.encode(credentials.clientSecret().orElseThrow(), StandardCharsets.UTF_8)
                    + "&scope=" + URLEncoder.encode(String.join(" ", scope), StandardCharsets.UTF_8);

            String url = "https://" + hostname + "/multipass/api/oauth2/token";

            SSLContext sslContext;
            sslContext = createSslContext(System.getenv("DEFAULT_CA_PATH"));

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
                    return Optional.of(tokenData.get("access_token").toString());
                }
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while fetching OAuth token", e);
        } catch (IOException e) {
            log.error("IOException while fetching OAuth token", e);
        }
        return Optional.empty();
    }

    private static SSLContext createSslContext(String caPath) {
        try {
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            try (InputStream is = new FileInputStream(caPath);
                    BufferedInputStream bis = new BufferedInputStream(is)) {
                int certIndex = 0;
                while (bis.available() > 0) {
                    Certificate cert = cf.generateCertificate(bis);
                    ks.setCertificateEntry("alias-" + certIndex, cert);
                    certIndex++;
                }
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
