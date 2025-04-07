/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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
package com.palantir.computemodules.client;

import com.palantir.computemodules.client.config.EnvVars;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Optional;

public final class ComputeModuleClient implements Client {
    private static final SafeLogger log = SafeLoggerFactory.get(ComputeModuleClient.class);

    private final HttpClient client;
    private final HttpRequest getRequest;
    private final HttpRequest.Builder postRequest;
    private final TaggedJobDeserializer deserializer = new TaggedJobDeserializer();

    public ComputeModuleClient() {
        String moduleAuthToken = EnvVars.Reserved.MODULE_AUTH_TOKEN.get();
        this.getRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:8946/job"))
                .header("Module-Auth-Token", moduleAuthToken)
                .build();
        this.postRequest = HttpRequest.newBuilder()
                .header("Module-Auth-Token", moduleAuthToken)
                .header("Content-Type", "application/octet-stream");
        this.client = HttpClient.newBuilder().build();
    }

    @Override
    public Optional<ComputeModuleJob> getJob() {
        try {
            HttpResponse<String> response = client.send(getRequest, BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return deserializer.deserialize(response.body());
            } else if (response.statusCode() == 204) {
                return Optional.empty();
            } else {
                log.error("Failed to request job", SafeArg.of("response", response));
                Thread.sleep(1000);
            }
        } catch (IOException e) {
            log.error("Connection error while requesting job", e);
        } catch (Exception e) {
            log.error("Failed to request job", e);
        }

        return Optional.empty();
    }

    @Override
    public void postResult(String jobId, InputStream result) {
        HttpRequest request = postRequest
                .copy()
                .uri(URI.create("http://127.0.0.1:8946/results" + "/" + jobId))
                .POST(BodyPublishers.ofInputStream(() -> result))
                .build();
        try {
            client.send(request, BodyHandlers.ofString());
        } catch (Exception e) {
            log.error("Failed to post result", SafeArg.of("jobId", jobId), e);
        }
    }
}
