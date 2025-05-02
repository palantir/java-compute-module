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
package com.palantir.computemodules;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palantir.computemodules.auth.AuthTokenResponse;
import com.palantir.computemodules.auth.ClientCredentialsTokenProvider;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ClientCredentialsTokenProviderTest {
    private static final String CLIENT_ID = "client_id";
    private static final String CLIENT_SECRET = "client_secret";
    private static final String hostname = "foundry.stack.com";
    private static final String hostnameWithScheme = "https://" + hostname;
    private static final String expectedTokenUrl = hostnameWithScheme + "/multipass/api/oauth2/token";
    private static final String scope1 = "my-app:view";
    private static final String scope2 = "my-app:write";
    private static final AuthTokenResponse mockOkTokenResponse =
            new AuthTokenResponse("dummy_token", scope1 + " " + scope2, 3600, "Bearer");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private HttpClient client;

    @Mock
    private HttpResponse<String> response;

    private ClientCredentialsTokenProvider fixture;

    @Test
    void get_provides_access_token() throws IOException, InterruptedException {
        fixture = ClientCredentialsTokenProvider.builder()
                .clientId(CLIENT_ID)
                .clientSecret(CLIENT_SECRET)
                .hostname(hostname)
                .scopes(scope1, scope2)
                .client(client)
                .build();

        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(objectMapper.writeValueAsString(mockOkTokenResponse));
        String token = fixture.get();
        assertNotNull(token);
        assertEquals(token, "dummy_token");
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        Mockito.verify(client).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest capturedRequest = requestCaptor.getValue();
        assertEquals(capturedRequest.uri().toString(), expectedTokenUrl);
    }

    @Test
    void provider_works_with_scheme_in_hostname() throws IOException, InterruptedException {
        fixture = ClientCredentialsTokenProvider.builder()
                .clientId(CLIENT_ID)
                .clientSecret(CLIENT_SECRET)
                .hostname(hostnameWithScheme)
                .scopes(scope1, scope2)
                .client(client)
                .build();

        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(objectMapper.writeValueAsString(mockOkTokenResponse));
        String token = fixture.get();
        assertNotNull(token);
        assertEquals(token, "dummy_token");
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        Mockito.verify(client).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest capturedRequest = requestCaptor.getValue();
        assertEquals(capturedRequest.uri().toString(), expectedTokenUrl);
    }

    @Test
    void throws_when_token_response_non_200() throws IOException, InterruptedException {
        fixture = ClientCredentialsTokenProvider.builder()
                .clientId(CLIENT_ID)
                .clientSecret(CLIENT_SECRET)
                .hostname(hostname)
                .scopes(scope1, scope2)
                .client(client)
                .build();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(400);
        assertThatThrownBy(() -> fixture.get()).isInstanceOf(NoSuchElementException.class);
    }
}
