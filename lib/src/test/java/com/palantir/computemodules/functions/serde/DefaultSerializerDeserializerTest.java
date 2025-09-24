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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        assertEquals(TEST_JOB_ID, okResult.jobId(), "Job ID should match input");

        String serialized = new String(okResult.result().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals("\"Hello World\"", serialized, "String should be serialized as JSON string");
    }

    @Test
    void test_serialize_integer() throws IOException {
        Integer input = 42;

        Result result = serializer.serialize(TEST_JOB_ID, input);

        assertInstanceOf(Ok.class, result, "Integer serialization should return Ok result");
        Ok okResult = (Ok) result;
        assertEquals(TEST_JOB_ID, okResult.jobId(), "Job ID should match input");

        String serialized = new String(okResult.result().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals("42", serialized, "Integer should be serialized as JSON number");
    }

    @Test
    void test_serialize_complex_object() throws IOException {
        TestObject input = new TestObject("test", 123, List.of("a", "b", "c"));

        Result result = serializer.serialize(TEST_JOB_ID, input);

        assertInstanceOf(Ok.class, result, "Complex object serialization should return Ok result");
        Ok okResult = (Ok) result;
        assertEquals(TEST_JOB_ID, okResult.jobId(), "Job ID should match input");

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
        assertEquals(TEST_JOB_ID, okResult.jobId(), "Job ID should match input");

        String serialized = new String(okResult.result().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(serialized.contains("2024"), "Serialized LocalDate should contain year 2024");
        assertTrue(serialized.contains("15"), "Serialized LocalDate should contain day 15");
    }

    @Test
    void test_serialize_local_date_time() throws IOException {
        LocalDateTime input = LocalDateTime.of(2024, Month.MARCH, 15, 14, 30, 45);

        Result result = serializer.serialize(TEST_JOB_ID, input);

        assertInstanceOf(Ok.class, result, "LocalDateTime serialization should return Ok result");
        Ok okResult = (Ok) result;
        assertEquals(TEST_JOB_ID, okResult.jobId(), "Job ID should match input");

        String serialized = new String(okResult.result().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(serialized.contains("2024"), "Serialized LocalDateTime should contain year 2024");
        assertTrue(serialized.contains("15"), "Serialized LocalDateTime should contain day 15");
        assertTrue(serialized.contains("14"), "Serialized LocalDateTime should contain hour 14");
        assertTrue(serialized.contains("30"), "Serialized LocalDateTime should contain minute 30");
        assertTrue(serialized.contains("45"), "Serialized LocalDateTime should contain second 45");
    }

    @Test
    void test_serialize_object_with_date_time_fields() throws IOException {
        DateTimeObject input = new DateTimeObject(
            LocalDate.of(2024, Month.JANUARY, 1),
            LocalDateTime.of(2024, Month.DECEMBER, 31, 23, 59, 59)
        );

        Result result = serializer.serialize(TEST_JOB_ID, input);

        assertInstanceOf(Ok.class, result, "DateTimeObject serialization should return Ok result");
        Ok okResult = (Ok) result;
        assertEquals(TEST_JOB_ID, okResult.jobId(), "Job ID should match input");

        String serialized = new String(okResult.result().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(serialized.contains("\"date\""), "Serialized object should contain date field");
        assertTrue(serialized.contains("\"dateTime\""), "Serialized object should contain dateTime field");
        assertTrue(serialized.contains("2024"), "Serialized object should contain year 2024");
    }

    @Test
    void test_deserialize_simple_string() throws Exception {
        DefaultDeserializer<String> stringDeserializer = new DefaultDeserializer<>();
        String inputObject = "Hello World";

        String result = stringDeserializer.deserialize(inputObject, String.class);

        assertEquals("Hello World", result, "Deserialized string should match original");
    }

    @Test
    void test_deserialize_integer() throws Exception {
        DefaultDeserializer<Integer> integerDeserializer = new DefaultDeserializer<>();
        Integer inputObject = 42;

        Integer result = integerDeserializer.deserialize(inputObject, Integer.class);

        assertEquals(42, result, "Deserialized integer should match original");
    }

    @Test
    void test_deserialize_number_to_integer() throws Exception {
        DefaultDeserializer<Integer> integerDeserializer = new DefaultDeserializer<>();
        Number inputObject = 42L; // Long to Integer conversion

        Integer result = integerDeserializer.deserialize(inputObject, Integer.class);

        assertEquals(42, result, "Deserialized Long should convert to Integer");
    }

    @Test
    void test_deserialize_complex_object() throws Exception {
        DefaultDeserializer<TestObject> testObjectDeserializer = new DefaultDeserializer<>();
        String json = "{\"name\":\"test\",\"value\":123,\"items\":[\"a\",\"b\",\"c\"]}";
        @SuppressWarnings("unchecked")
        Map<String, Object> inputMap = mapper.readValue(json, Map.class);

        TestObject result = testObjectDeserializer.deserialize(inputMap, TestObject.class);

        assertNotNull(result, "Deserialized object should not be null");
        assertEquals("test", result.name(), "Deserialized name should match original");
        assertEquals(123, result.value(), "Deserialized value should match original");
        assertEquals(List.of("a", "b", "c"), result.items(), "Deserialized items should match original");
    }

    @Test
    void test_deserialize_local_date() throws Exception {
        DefaultDeserializer<LocalDate> localDateDeserializer = new DefaultDeserializer<>();
        List<Integer> inputArray = List.of(2024, 3, 15);

        LocalDate result = localDateDeserializer.deserialize(inputArray, LocalDate.class);

        assertNotNull(result, "Deserialized LocalDate should not be null");
        assertEquals(LocalDate.of(2024, Month.MARCH, 15), result, "Deserialized LocalDate should match original");
    }

    @Test
    void test_deserialize_local_date_time() throws Exception {
        DefaultDeserializer<LocalDateTime> localDateTimeDeserializer = new DefaultDeserializer<>();
        List<Integer> inputArray = List.of(2024, 3, 15, 14, 30, 45);

        LocalDateTime result = localDateTimeDeserializer.deserialize(inputArray, LocalDateTime.class);

        assertNotNull(result, "Deserialized LocalDateTime should not be null");
        assertEquals(LocalDateTime.of(2024, Month.MARCH, 15, 14, 30, 45), result, "Deserialized LocalDateTime should match original");
    }

    @Test
    void test_deserialize_object_with_date_time_fields() throws Exception {
        DefaultDeserializer<DateTimeObject> dateTimeObjectDeserializer = new DefaultDeserializer<>();
        String json = "{\"date\":[2024,1,1],\"dateTime\":[2024,12,31,23,59,59]}";
        @SuppressWarnings("unchecked")
        Map<String, Object> inputMap = mapper.readValue(json, Map.class);

        DateTimeObject result = dateTimeObjectDeserializer.deserialize(inputMap, DateTimeObject.class);

        assertNotNull(result, "Deserialized DateTimeObject should not be null");
        assertEquals(LocalDate.of(2024, Month.JANUARY, 1), result.date(), "Deserialized date should match original");
        assertEquals(LocalDateTime.of(2024, Month.DECEMBER, 31, 23, 59, 59), result.dateTime(), "Deserialized dateTime should match original");
    }

    @Test
    void test_serialize_null_input() {
        Result result = serializer.serialize(TEST_JOB_ID, null);

        assertInstanceOf(Ok.class, result, "Null input serialization should return Ok result");
        Ok okResult = (Ok) result;
        assertEquals(TEST_JOB_ID, okResult.jobId(), "Job ID should match input");
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

        assertEquals(original, deserialized, "Round-trip object should equal original");
    }

    @Test
    void test_round_trip_with_date_time_object() throws Exception {
        DefaultDeserializer<DateTimeObject> dateTimeObjectDeserializer = new DefaultDeserializer<>();
        DateTimeObject original = new DateTimeObject(
            LocalDate.of(2024, Month.JUNE, 15),
            LocalDateTime.of(2024, Month.JUNE, 15, 12, 0, 0)
        );

        Result serializeResult = serializer.serialize(TEST_JOB_ID, original);
        assertInstanceOf(Ok.class, serializeResult, "DateTimeObject round-trip serialization should return Ok result");
        Ok okResult = (Ok) serializeResult;

        String json = new String(okResult.result().readAllBytes(), StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> inputMap = mapper.readValue(json, Map.class);

        DateTimeObject deserialized = dateTimeObjectDeserializer.deserialize(inputMap, DateTimeObject.class);

        assertEquals(original, deserialized, "Round-trip DateTimeObject should equal original");
    }

    private record TestObject(String name, int value, List<String> items) {}

    private record DateTimeObject(LocalDate date, LocalDateTime dateTime) {}
}