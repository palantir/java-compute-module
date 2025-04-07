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
package com.palantir.computemodules;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.computemodules.client.Client;
import com.palantir.computemodules.client.ComputeModuleClient;
import com.palantir.computemodules.client.ComputeModuleJob;
import com.palantir.computemodules.functions.Context;
import com.palantir.computemodules.functions.Function;
import com.palantir.computemodules.functions.FunctionRunner;
import com.palantir.computemodules.functions.results.Failed;
import com.palantir.computemodules.functions.results.Ok;
import com.palantir.computemodules.functions.results.Result;
import com.palantir.computemodules.functions.serde.DefaultDeserializer;
import com.palantir.computemodules.functions.serde.DefaultSerializer;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ComputeModule {

    private static final SafeLogger log = SafeLoggerFactory.get(ComputeModule.class);

    private final Map<String, FunctionRunner<?, ?>> functions;
    private final Client client;
    private final ListeningExecutorService executor;

    public static ComputeModuleBuilder builder() {
        return new ComputeModuleBuilder();
    }

    /*
     * Starts the client polling loop. This is blocking, run in the background needed.
     */
    public Void start() {
        while (true) {
            client.getJob().ifPresent(job -> {
                ListenableFuture<Result> future = executor.submit(() -> execute(job));
                Futures.addCallback(
                        future,
                        new FutureCallback<Result>() {

                            @Override
                            public void onSuccess(Result result) {
                                switch (result) {
                                    case Ok ok:
                                        client.postResult(ok.jobId(), ok.result());
                                        break;
                                    case Failed failed:
                                        client.postResult(failed.jobId(), serializeException(failed));
                                        break;
                                }
                            }

                            @Override
                            public void onFailure(Throwable throwable) {
                                Failed failed = new Failed(job.jobId(), new Exception(throwable));
                                client.postResult(failed.jobId(), serializeException(failed));
                            }
                        },
                        executor);
            });
        }
    }

    private Result execute(ComputeModuleJob job) {
        if (functions.containsKey(job.queryType())) {
            return functions.get(job.queryType()).run(new Context(job.jobId()), job.query());
        } else {
            return new Failed(
                    job.jobId(),
                    new SafeRuntimeException(
                            "Requested function not found",
                            SafeArg.of("requested", job.queryType()),
                            SafeArg.of("known", functions.keySet())));
        }
    }

    private InputStream serializeException(Failed failed) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter pw =
                new PrintWriter(new BufferedWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8)))) {
            pw.println("JobId: " + failed.jobId());
            failed.e().printStackTrace(pw);
        } catch (Exception e) {
            log.error("Failed to serialized exception", SafeArg.of("jobId", failed.jobId()), e);
            String message = "Exception serializing exception for job, check logs: " + failed.jobId();
            return new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8));
        }
        return new ByteArrayInputStream(baos.toByteArray());
    }

    private ComputeModule(
            Client client, ListeningExecutorService executor, Map<String, FunctionRunner<?, ?>> functions) {
        this.client = client;
        this.executor = executor;
        this.functions = functions;
    }

    public static final class ComputeModuleBuilder {
        private final Map<String, FunctionRunner<?, ?>> functions;
        private Optional<Client> client =
                Optional.empty(); // ComputeModuleClient construction is deferred due to env vars
        private ListeningExecutorService executor =
                MoreExecutors.listeningDecorator(Executors.newVirtualThreadPerTaskExecutor());

        private ComputeModuleBuilder() {
            functions = new HashMap<>();
        }

        /*
         * Adds a Function. Type markers are required for ser/de. Will be callable via the provided name.
         */
        public <I, O> ComputeModuleBuilder add(
                Function<I, O> function, Class<I> inputType, Class<O> outputType, String name) {
            functions.put(
                    name,
                    new FunctionRunner<>(
                            function, inputType, outputType, new DefaultDeserializer<I>(), new DefaultSerializer<O>()));
            return this;
        }

        /*
         * Adds a FunctionRunner to be callable via name. Use this if you want to override the default serializer or
         * deserializer for this function. Will be callable via the provided name.
         */
        public <I, O> ComputeModuleBuilder add(FunctionRunner<I, O> runner, String name) {
            functions.put(name, runner);
            return this;
        }

        /*
         * Not required, if unused the default client will be provided. This is useful for unit testing.
         */
        public ComputeModuleBuilder withClient(Client newClient) {
            this.client = Optional.of(newClient);
            return this;
        }

        /*
         * Not required, if unused the default executor will use virtual threads. Each job is ran on it's own
         * thread spawned from this ExecutorService, results are posted in a callback scheduled on this executor.
         */
        public ComputeModuleBuilder withExecutor(ExecutorService newExecutor) {
            this.executor = MoreExecutors.listeningDecorator(newExecutor);
            return this;
        }

        public ComputeModule build() {
            return new ComputeModule(client.orElseGet(() -> new ComputeModuleClient()), executor, functions);
        }
    }
}
