/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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
package com.palantir.computemodules.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class SlsLayoutTest {

    private final SlsLayout layout = new SlsLayout();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void before() {
        layout.start();
    }

    @AfterEach
    void after() {
        MDC.clear();
        layout.stop();
    }

    @Test
    @SuppressWarnings("unchecked")
    void produces_valid_sls_json_structure() throws Exception {
        LoggingEvent event = createEvent(Level.INFO, "Test message");
        String output = layout.doLayout(event);

        Map<String, Object> parsed = MAPPER.readValue(output, Map.class);
        assertThat(parsed.get("type")).isEqualTo("service.1");
        assertThat(parsed.get("level")).isEqualTo("INFO");
        assertThat(parsed.get("time")).isNotNull();
        assertThat(parsed.get("origin")).isNotNull();
        assertThat(parsed.get("safe")).isEqualTo(true);
        assertThat(parsed.get("thread")).isNotNull();
        assertThat(parsed.get("message")).isEqualTo("Test message");
        assertThat(parsed).containsKey("params");
        assertThat(parsed).containsKey("unsafeParams");
        assertThat(parsed).containsKey("tags");
        assertThat(output).endsWith("\n");
    }

    @Test
    @SuppressWarnings("unchecked")
    void includes_mdc_values_in_params() throws Exception {
        MDC.put("session_id", "test-session-123");
        MDC.put("job_id", "test-job-456");

        LoggingEvent event = createEvent(Level.INFO, "With context");
        String output = layout.doLayout(event);

        Map<String, Object> parsed = MAPPER.readValue(output, Map.class);
        Map<String, String> params = (Map<String, String>) parsed.get("params");
        assertThat(params.get("session_id")).isEqualTo("test-session-123");
        assertThat(params.get("job_id")).isEqualTo("test-job-456");
    }

    @Test
    @SuppressWarnings("unchecked")
    void empty_params_when_no_mdc() throws Exception {
        LoggingEvent event = createEvent(Level.INFO, "No context");
        String output = layout.doLayout(event);

        Map<String, Object> parsed = MAPPER.readValue(output, Map.class);
        Map<String, String> params = (Map<String, String>) parsed.get("params");
        assertThat(params).isEmpty();
    }

    @Test
    void includes_exception_in_message() throws Exception {
        LoggingEvent event = createEvent(Level.ERROR, "Something failed");
        event.setThrowableProxy(new ThrowableProxy(new RuntimeException("boom")));
        String output = layout.doLayout(event);

        Map<String, Object> parsed = MAPPER.readValue(output, Map.class);
        String message = (String) parsed.get("message");
        assertThat(message).startsWith("Something failed\n");
        assertThat(message).contains("RuntimeException");
        assertThat(message).contains("boom");
    }

    @Test
    @SuppressWarnings("unchecked")
    void maps_log_levels_correctly() throws Exception {
        for (Level level : new Level[] {Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR}) {
            LoggingEvent event = createEvent(level, "level test");
            String output = layout.doLayout(event);
            Map<String, Object> parsed = MAPPER.readValue(output, Map.class);
            assertThat(parsed.get("level")).isEqualTo(level.toString());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void uses_logger_name_as_origin() throws Exception {
        LoggingEvent event = createEvent(Level.INFO, "origin test");
        String output = layout.doLayout(event);

        Map<String, Object> parsed = MAPPER.readValue(output, Map.class);
        assertThat((String) parsed.get("origin")).contains("SlsLayoutTest");
    }

    @Test
    @SuppressWarnings("unchecked")
    void includes_safe_args_in_params() throws Exception {
        LoggingEvent event = createEvent(Level.INFO, "Adding numbers");
        event.setArgumentArray(new Object[] {SafeArg.of("a", 1), SafeArg.of("b", 2)});
        String output = layout.doLayout(event);

        Map<String, Object> parsed = MAPPER.readValue(output, Map.class);
        Map<String, Object> params = (Map<String, Object>) parsed.get("params");
        assertThat(params).containsEntry("a", 1);
        assertThat(params).containsEntry("b", 2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void includes_unsafe_args_in_unsafe_params() throws Exception {
        LoggingEvent event = createEvent(Level.INFO, "User login");
        event.setArgumentArray(new Object[] {SafeArg.of("userId", 42), UnsafeArg.of("email", "test@example.com")});
        String output = layout.doLayout(event);

        Map<String, Object> parsed = MAPPER.readValue(output, Map.class);
        Map<String, Object> params = (Map<String, Object>) parsed.get("params");
        Map<String, Object> unsafeParams = (Map<String, Object>) parsed.get("unsafeParams");
        assertThat(params).containsEntry("userId", 42);
        assertThat(unsafeParams).containsEntry("email", "test@example.com");
    }

    private LoggingEvent createEvent(Level level, String message) {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(SlsLayoutTest.class);
        return new LoggingEvent(SlsLayoutTest.class.getName(), logger, level, message, null, null);
    }
}
