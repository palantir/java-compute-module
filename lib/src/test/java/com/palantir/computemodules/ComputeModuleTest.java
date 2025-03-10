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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.computemodules.client.TestClient;
import com.palantir.computemodules.functions.Context;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ComputeModuleTest {

    private static final ListeningExecutorService executor =
            MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
    private static final TestClient testClient = new TestClient();
    private static final ComputeModule cm = ComputeModule.builder()
            .add(ComputeModuleTest::dub, Integer.class, Integer.class, "dub")
            .add(ComputeModuleTest::hello, String.class, String.class, "hello")
            .add(ComputeModuleTest::mult, Event.class, Integer.class, "mult")
            .add(ComputeModuleTest::error, Integer.class, Integer.class, "error")
            .withClient(testClient)
            .build();

    @BeforeAll
    static void before() {
        executor.execute(cm::start);
    }

    @Test
    void test_example_compute_module() {
        String job1 = testClient.submit("hello", "Compute Module");
        Integer result2 = testClient.execute("dub", 2, Integer.class);
        String job3 = testClient.submit("mult", new Event(4, 5));
        Integer result3 = testClient.result(job3, Integer.class);
        String result1 = testClient.result(job1, String.class);

        assertEquals(result1, "hello Compute Module");
        assertEquals(result2, 4);
        assertEquals(result3, 20);
    }

    @Test
    void test_invalid_function_name() throws IOException {
        InputStream result = testClient.execute("doesn't exist", 2);
        String error = new String(result.readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(error.contains("Requested function not found"), true);
    }

    @Test
    void test_user_function_that_throws() throws IOException {
        InputStream result = testClient.execute("error", 2);
        String error = new String(result.readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(error.contains("Intentionally throwing: 2"), true);
    }

    static Integer dub(Context context, Integer input) {
        return input * 2;
    }

    @SuppressWarnings("DoNotCallSuggester")
    static Integer error(Context context, Integer input) {
        throw new RuntimeException("Intentionally throwing: " + input);
    }

    static String hello(Context context, String name) {
        return "hello " + name;
    }

    private record Event(int x, int y) {}

    static Integer mult(Context context, Event event) {
        return event.x() * event.y();
    }
}
