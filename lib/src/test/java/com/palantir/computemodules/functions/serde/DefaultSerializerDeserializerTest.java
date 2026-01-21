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
package com.palantir.computemodules.functions.serde;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palantir.computemodules.functions.results.Ok;
import com.palantir.computemodules.functions.results.Result;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultSerializerDeserializerTest {

    private static final String TEST_JOB_ID = "test-job-123";
    private final DefaultSerializer<Object> serializer = new DefaultSerializer<>();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void test_serialize_simple_string() throws IOException {
        String input = "Hello World";

        Result result = serializer.serialize(TEST_JOB_ID, input);

        assertInstanceOf(Ok.class, result, "String serialization should return Ok result");
        Ok okResult = (Ok) result;
        assertThat(okResult.jobId()).describedAs("Job ID should match input").isEqualTo(TEST_JOB_ID);

        String serialized = new String(okResult.result().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(serialized)
                .describedAs("String should be serialized as JSON string")
                .isEqualTo("\"Hello World\"");
    }

    @Test
    void test_serialize_integer() throws IOException {
        Integer input = 42;

        Result result = serializer.serialize(TEST_JOB_ID, input);

        assertInstanceOf(Ok.class, result, "Integer serialization should return Ok result");
        Ok okResult = (Ok) result;
        assertThat(okResult.jobId()).describedAs("Job ID should match input").isEqualTo(TEST_JOB_ID);

        String serialized = new String(okResult.result().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(serialized)
                .describedAs("Integer should be serialized as JSON number")
                .isEqualTo("42");
    }

    @Test
    void test_serialize_complex_object() throws IOException {
        TestObject input = new TestObject("test", 123, List.of("a", "b", "c"));

        Result result = serializer.serialize(TEST_JOB_ID, input);

        assertInstanceOf(Ok.class, result, "Complex object serialization should return Ok result");
        Ok okResult = (Ok) result;
        assertThat(okResult.jobId()).describedAs("Job ID should match input").isEqualTo(TEST_JOB_ID);

        String serialized = new String(okResult.result().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(serialized.contains("\"name\":\"test\""), "Serialized JSON should contain name field");
        assertTrue(serialized.contains("\"value\":123"), "Serialized JSON should contain value field");
        assertTrue(serialized.contains("\"items\":[\"a\",\"b\",\"c\"]"), "Serialized JSON should contain items array");
    }

    @Test
    void test_serialize_local_date() throws IOException {
        LocalDate input = LocalDate.of(2024, Month.MARCH, 15);

        Result result = serializer.serialize(TEST_JOB_ID, input);

        assertInstanceOf(Ok.class, result, "Serialization of LocalDate failed with " + result);
        Ok okResult = (Ok) result;
        assertThat(okResult.jobId()).describedAs("Job ID should match input").isEqualTo(TEST_JOB_ID);

        String serialized = new String(okResult.result().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(serialized.equals("\"2024-03-15\""), "Serialized LocalDate should match input");
    }

    @Test
    void test_serialize_local_date_time() throws IOException {
        LocalDateTime input = LocalDateTime.of(2024, Month.MARCH, 15, 14, 30, 45);

        Result result = serializer.serialize(TEST_JOB_ID, input);

        assertInstanceOf(Ok.class, result, "LocalDateTime serialization should return Ok result");
        Ok okResult = (Ok) result;
        assertThat(okResult.jobId()).describedAs("Job ID should match input").isEqualTo(TEST_JOB_ID);

        String serialized = new String(okResult.result().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(serialized)
                .describedAs("Serialized LocalDateTime should match input")
                .isEqualTo("\"2024-03-15T14:30:45\"");
    }

    @Test
    void test_serialize_object_with_date_time_fields() throws IOException {
        DateTimeObject input = new DateTimeObject(
                LocalDate.of(2024, Month.JANUARY, 1), LocalDateTime.of(2024, Month.DECEMBER, 31, 23, 59, 59));

        Result result = serializer.serialize(TEST_JOB_ID, input);

        assertInstanceOf(Ok.class, result, "DateTimeObject serialization should return Ok result");
        Ok okResult = (Ok) result;
        assertThat(okResult.jobId()).describedAs("Job ID should match input").isEqualTo(TEST_JOB_ID);

        String serialized = new String(okResult.result().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(serialized).isEqualTo("{\"date\":\"2024-01-01\",\"dateTime\":\"2024-12-31T23:59:59\"}");
    }

    @Test
    void test_deserialize_simple_string() throws Exception {
        DefaultDeserializer<String> stringDeserializer = new DefaultDeserializer<>();
        String inputObject = "Hello World";

        String result = stringDeserializer.deserialize(inputObject, String.class);

        assertThat(result)
                .describedAs("Deserialized string should match original")
                .isEqualTo("Hello World");
    }

    @Test
    void test_deserialize_integer() throws Exception {
        DefaultDeserializer<Integer> integerDeserializer = new DefaultDeserializer<>();
        Integer inputObject = 42;

        Integer result = integerDeserializer.deserialize(inputObject, Integer.class);

        assertThat(result)
                .describedAs("Deserialized integer should match original")
                .isEqualTo(42);
    }

    @Test
    void test_deserialize_number_to_integer() throws Exception {
        DefaultDeserializer<Integer> integerDeserializer = new DefaultDeserializer<>();
        Number inputObject = 42L; // Long to Integer conversion

        Integer result = integerDeserializer.deserialize(inputObject, Integer.class);

        assertThat(result)
                .describedAs("Deserialized Long should convert to Integer")
                .isEqualTo(42);
    }

    @Test
    void test_deserialize_complex_object() throws Exception {
        DefaultDeserializer<TestObject> testObjectDeserializer = new DefaultDeserializer<>();
        String json = "{\"name\":\"test\",\"value\":123,\"items\":[\"a\",\"b\",\"c\"]}";
        @SuppressWarnings("unchecked")
        Map<String, Object> inputMap = mapper.readValue(json, Map.class);

        TestObject result = testObjectDeserializer.deserialize(inputMap, TestObject.class);

        assertThat(result).describedAs("Deserialized object should not be null").isNotNull();
        assertThat(result.name())
                .describedAs("Deserialized name should match original")
                .isEqualTo("test");
        assertThat(result.value())
                .describedAs("Deserialized value should match original")
                .isEqualTo(123);
        assertThat(result.items())
                .describedAs("Deserialized items should match original")
                .containsExactly("a", "b", "c");
    }

    @Test
    void test_deserialize_local_date() throws Exception {
        DefaultDeserializer<LocalDate> localDateDeserializer = new DefaultDeserializer<>();
        List<Integer> inputArray = List.of(2024, 3, 15);

        LocalDate result = localDateDeserializer.deserialize(inputArray, LocalDate.class);

        assertThat(result)
                .describedAs("Deserialized LocalDate should not be null")
                .isNotNull();
        assertThat(result)
                .describedAs("Deserialized LocalDate should match original")
                .isEqualTo(LocalDate.of(2024, Month.MARCH, 15));
    }

    @Test
    void test_deserialize_local_date_time() throws Exception {
        DefaultDeserializer<LocalDateTime> localDateTimeDeserializer = new DefaultDeserializer<>();
        List<Integer> inputArray = List.of(2024, 3, 15, 14, 30, 45);

        LocalDateTime result = localDateTimeDeserializer.deserialize(inputArray, LocalDateTime.class);

        assertThat(result)
                .describedAs("Deserialized LocalDateTime should not be null")
                .isNotNull();
        assertThat(result)
                .describedAs("Deserialized LocalDateTime should match original")
                .isEqualTo(LocalDateTime.of(2024, Month.MARCH, 15, 14, 30, 45));
    }

    @Test
    void test_deserialize_object_with_date_time_fields() throws Exception {
        DefaultDeserializer<DateTimeObject> dateTimeObjectDeserializer = new DefaultDeserializer<>();
        String json = "{\"date\":\"2024-01-01\",\"dateTime\":\"2024-12-31T23:59:59\"}";
        @SuppressWarnings("unchecked")
        Map<String, Object> inputMap = mapper.readValue(json, Map.class);

        DateTimeObject result = dateTimeObjectDeserializer.deserialize(inputMap, DateTimeObject.class);

        assertThat(result)
                .describedAs("Deserialized DateTimeObject should not be null")
                .isNotNull();
        assertThat(result.date())
                .describedAs("Deserialized date should match original")
                .isEqualTo(LocalDate.of(2024, Month.JANUARY, 1));
        assertThat(result.dateTime())
                .describedAs("Deserialized dateTime should match")
                .isEqualTo(LocalDateTime.of(2024, Month.DECEMBER, 31, 23, 59, 59));
    }

    @Test
    void test_serialize_null_input() {
        Result result = serializer.serialize(TEST_JOB_ID, null);

        assertInstanceOf(Ok.class, result, "Null input serialization should return Ok result");
        Ok okResult = (Ok) result;
        assertThat(okResult.jobId()).describedAs("Job ID should match input").isEqualTo(TEST_JOB_ID);
    }

    @Test
    void test_round_trip_serialization_deserialization() throws Exception {
        DefaultDeserializer<TestObject> testObjectDeserializer = new DefaultDeserializer<>();
        TestObject original = new TestObject("round-trip", 999, List.of("x", "y", "z"));

        Result serializeResult = serializer.serialize(TEST_JOB_ID, original);
        assertInstanceOf(Ok.class, serializeResult, "Round-trip serialization should return Ok result");
        Ok okResult = (Ok) serializeResult;

        String json = new String(okResult.result().readAllBytes(), StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> inputMap = mapper.readValue(json, Map.class);

        TestObject deserialized = testObjectDeserializer.deserialize(inputMap, TestObject.class);

        assertThat(deserialized)
                .describedAs("Round-trip object should equal original")
                .isEqualTo(original);
    }

    @Test
    void test_round_trip_with_date_time_object() throws Exception {
        DefaultDeserializer<DateTimeObject> dateTimeObjectDeserializer = new DefaultDeserializer<>();
        DateTimeObject original = new DateTimeObject(
                LocalDate.of(2024, Month.JUNE, 15), LocalDateTime.of(2024, Month.JUNE, 15, 12, 0, 0));

        Result serializeResult = serializer.serialize(TEST_JOB_ID, original);
        assertInstanceOf(Ok.class, serializeResult, "DateTimeObject round-trip serialization should return Ok result");
        Ok okResult = (Ok) serializeResult;

        String json = new String(okResult.result().readAllBytes(), StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> inputMap = mapper.readValue(json, Map.class);

        DateTimeObject deserialized = dateTimeObjectDeserializer.deserialize(inputMap, DateTimeObject.class);

        assertThat(deserialized)
                .describedAs("Round-trip DateTimeObject should equal original")
                .isEqualTo(original);
    }

    private record TestObject(String name, int value, List<String> items) {}

    private record DateTimeObject(LocalDate date, LocalDateTime dateTime) {}
}
