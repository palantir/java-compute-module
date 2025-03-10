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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/*
 * This client exists for usage in unit tests, do not deploy.
 *
 * Do not use getJob and postResult, doing so may result in unexpected issues.
 * Instead use execute, or submit and result.
 *
 */
public final class TestClient implements Client {

    private static final ObjectMapper mapper = new ObjectMapper();
    private BlockingQueue<ComputeModuleJob> jobs = new LinkedBlockingQueue<>();
    private Map<String, BlockingQueue<InputStream>> results = new ConcurrentHashMap<>();

    @Override
    public Optional<ComputeModuleJob> getJob() {
        try {
            return Optional.of(jobs.take());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void postResult(String jobId, InputStream result) {
        try {
            Optional.ofNullable(results.get(jobId)).orElseThrow().put(result);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /*
     * Executes a test job and returns a result stream
     */
    public <I> InputStream execute(String queryType, I input) {
        return result(submit(queryType, input));
    }

    /*
     * Executes a test job and returns the deserialized result type using a provided deserializer
     */
    public <I, O> O execute(String queryType, I input, Class<O> outputType) {
        return result(submit(queryType, input), outputType);
    }

    /*
     * Submits a job and returns a jobId. This job will get executed in the background. You can await the result using
     * one of the results methods.
     */
    public <I> String submit(String queryType, I input) {
        String jobId = UUID.randomUUID().toString();
        results.put(jobId, new ArrayBlockingQueue<>(1));
        try {
            jobs.put(new ComputeModuleJob(jobId, queryType, input));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return jobId;
    }

    /*
     * Awaits the result for the provided jobId. If the jobId was never present this may return null. If the
     * jobId was already takend this may block forever.
     */
    public InputStream result(String jobId) {
        try {
            return Optional.ofNullable(results.get(jobId)).orElseThrow().take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /*
     * Awaits the result for the provided jobId. If the jobId was never present this may return null. If the
     * jobId was already takend this may block forever.
     */
    public <O> O result(String jobId, Class<O> outputType) {
        try {
            return mapper.readValue(
                    Optional.ofNullable(results.get(jobId)).orElseThrow().take(), outputType);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
