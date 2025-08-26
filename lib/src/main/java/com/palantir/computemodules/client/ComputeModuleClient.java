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
    private static final Integer POST_RESULT_MAX_ATTEMPTS = 5;
    private static final Integer POST_ERROR_MAX_ATTEMPTS = 3;

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
        String error = "";
        try {
            for (Integer i = 0; i < POST_RESULT_MAX_ATTEMPTS; i++) {
                HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
                if (response.statusCode() == 204) {
                    log.info("Successfully posted result", SafeArg.of("jobId", jobId));
                    return;
                }
                error = new String(
                        "Failed to post error for jobId: " + jobId + ", statusCode: " + response.statusCode());
                log.error("Failed to post error", SafeArg.of("jobId", error));
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            error = new String("Failed to post error for jobId: " + jobId + "error: " + e.toString());
            log.error("Failed to post result", SafeArg.of("jobId", jobId), e);
        }
        log.error(
                "Failed to post result after several attempts. Now attempting to return the error as the result. ",
                SafeArg.of("jobId", jobId),
                SafeArg.of("error", error),
                SafeArg.of("attempts", POST_RESULT_MAX_ATTEMPTS));
        postError(jobId, error);
    }

    private void postError(String jobId, String errorString) {
        HttpRequest request = postRequest
                .copy()
                .uri(URI.create("http://127.0.0.1:8946/results" + "/" + jobId))
                .POST(BodyPublishers.ofString(errorString))
                .build();
        for (Integer i = 0; i < POST_ERROR_MAX_ATTEMPTS; i++) {
            try {
                HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
                if (response.statusCode() == 204) {
                    log.info("Successfully posted error", SafeArg.of("jobId", jobId));
                    return;
                }
                log.error("Failed to post error", SafeArg.of("jobId", jobId), SafeArg.of("response", response));
                Thread.sleep(1000);
            } catch (Exception e) {
                log.error("Failed to post error", SafeArg.of("jobId", jobId), e);
            }
        }
    }

    @Override
    public void postRestart() {
        HttpRequest request = postRequest
                .copy()
                .uri(URI.create("http://127.0.0.1:8946/restart-notify"))
                .POST(BodyPublishers.ofString(""))
                .build();
        try {
            client.send(request, BodyHandlers.ofString());
        } catch (Exception e) {
            log.error("Failed to post restart", e);
        }
    }
}
