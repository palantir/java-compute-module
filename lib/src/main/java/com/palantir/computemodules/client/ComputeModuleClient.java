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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.palantir.computemodules.client.config.EnvVars;
import com.palantir.computemodules.functions.api.FunctionRunnerSchema;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Optional;

public final class ComputeModuleClient implements Client {
    private static final SafeLogger log = SafeLoggerFactory.get(ComputeModuleClient.class);
    private static final Integer POST_RESULT_MAX_ATTEMPTS = 5;
    private static final Integer POST_ERROR_MAX_ATTEMPTS = 3;
    private static final Integer POST_RESTART_MAX_ATTEMPTS = 5;
    private static final int POST_SCHEMAS_MAX_ATTEMPTS = 5;
    private static final ObjectMapper mapper = new ObjectMapper().registerModule(new Jdk8Module());

    private final HttpClient client;
    private final HttpRequest getRequest;
    private final HttpRequest.Builder postRequest;
    private final HttpRequest.Builder postSchemasRequest;
    private final TaggedJobDeserializer deserializer = new TaggedJobDeserializer();

    public ComputeModuleClient() {
        this.getRequest = HttpRequest.newBuilder()
                .uri(URI.create(EnvVars.Reserved.GET_JOB_URI_V2.get()))
                .build();
        this.postRequest = HttpRequest.newBuilder().header("Content-Type", "application/octet-stream");
        this.postSchemasRequest = HttpRequest.newBuilder()
                .uri(URI.create(EnvVars.Reserved.POST_SCHEMA_URI_V2.get()))
                .header("Content-Type", "application/json");
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
        String error = "";
        try {
            // Buffer the result so retries resend the full body instead of an already-consumed stream.
            HttpRequest request = postRequest
                    .copy()
                    .uri(URI.create(String.format("%s/%s", EnvVars.Reserved.POST_RESULT_URI_V2.get(), jobId)))
                    .POST(BodyPublishers.ofByteArray(result.readAllBytes()))
                    .build();
            for (Integer i = 0; i < POST_RESULT_MAX_ATTEMPTS; i++) {
                try {
                    HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
                    if (isSuccess(response)) {
                        return;
                    }
                    error = new String("Failed to post result, statusCode: " + response.statusCode());
                    log.error("Failed to post result", SafeArg.of("error", error));
                    Thread.sleep(1000);
                } catch (IOException e) {
                    error = new String("Failed to post result, error: " + e.toString());
                    log.error("Failed to post result", e);
                }
            }
        } catch (Exception e) {
            error = new String("Failed to post result, error: " + e.toString());
            log.error("Failed to post result", e);
        }
        log.error(
                "Failed to post result after several attempts. Now attempting to return the error as the result. ",
                SafeArg.of("error", error),
                SafeArg.of("attempts", POST_RESULT_MAX_ATTEMPTS));
        postError(jobId, error);
    }

    private void postError(String jobId, String errorString) {
        HttpRequest request = postRequest
                .copy()
                .uri(URI.create(String.format("%s/%s", EnvVars.Reserved.POST_RESULT_URI_V2.get(), jobId)))
                .POST(BodyPublishers.ofString(errorString))
                .build();
        for (Integer i = 0; i < POST_ERROR_MAX_ATTEMPTS; i++) {
            try {
                HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
                if (isSuccess(response)) {
                    log.info("Successfully posted error");
                    return;
                }
                log.error("Failed to post error", SafeArg.of("response", response));
                Thread.sleep(1000);
            } catch (Exception e) {
                log.error("Failed to post error", e);
            }
        }
    }

    // The forwarder responds 202 Accepted, so treat any 2xx as success.
    private static boolean isSuccess(HttpResponse<String> response) {
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    @Override
    public void postRestart() {
        HttpRequest request = postRequest
                .copy()
                .uri(URI.create("http://127.0.0.1:8946/restart-notify"))
                .POST(BodyPublishers.ofString(""))
                .build();
        for (Integer i = 0; i < POST_RESTART_MAX_ATTEMPTS; i++) {
            try {
                HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    log.warn("Successfully posted restart", UnsafeArg.of("removedJobIds", response.body()));
                    return;
                }
                log.error("Failed to post restart", SafeArg.of("response", response));
            } catch (Exception e) {
                log.error("Failed to post restart", e);
            }
            try {
                // the forwarder may not be listening yet right after container start
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.error("Unable to post restart", SafeArg.of("attempts", POST_RESTART_MAX_ATTEMPTS));
    }

    @Override
    public void postSchemas(List<FunctionRunnerSchema> functionSchemas) {
        log.debug("Posting function schemas", UnsafeArg.of("schemas", functionSchemas));
        byte[] body;
        try {
            body = mapper.writeValueAsBytes(functionSchemas);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize function schemas", e);
            return;
        }

        for (int attempt = 0; attempt < POST_SCHEMAS_MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest request = postSchemasRequest
                        .copy()
                        .POST(BodyPublishers.ofByteArray(body))
                        .build();
                HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
                log.debug(
                        "POST /schemas response",
                        SafeArg.of("statusCode", response.statusCode()),
                        SafeArg.of("body", response.body()));
                return;
            } catch (ConnectException e) {
                long sleepMs = (long) Math.pow(2, attempt) * 1000;
                log.warn(
                        "POST /schemas connection refused, retrying",
                        SafeArg.of("attempt", attempt + 1),
                        SafeArg.of("sleepMs", sleepMs));
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("Interrupted while waiting to retry POST /schemas", ie);
                    return;
                }
            } catch (IOException e) {
                log.error("IO error posting function schemas", e);
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while posting function schemas", e);
                return;
            }
        }
        log.error("Failed to POST /schemas after max attempts", SafeArg.of("attempts", POST_SCHEMAS_MAX_ATTEMPTS));
    }
}
