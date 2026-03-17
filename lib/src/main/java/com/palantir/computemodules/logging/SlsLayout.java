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

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.LayoutBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palantir.logsafe.Arg;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.Unsafe;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SlsLayout extends LayoutBase<ILoggingEvent> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    @Unsafe
    @Override
    public String doLayout(ILoggingEvent event) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", "service.1");
        envelope.put("level", event.getLevel().toString());
        envelope.put("time", ISO_FORMATTER.format(Instant.ofEpochMilli(event.getTimeStamp())));
        envelope.put("origin", event.getLoggerName());
        envelope.put("safe", true);
        envelope.put("thread", event.getThreadName());

        String message = event.getFormattedMessage();
        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy != null) {
            message = message + "\n" + ThrowableProxyUtil.asString(throwableProxy);
        }
        envelope.put("message", message);

        Map<String, String> mdcMap = event.getMDCPropertyMap();
        Map<String, Object> params = new LinkedHashMap<>();
        copyIfPresent(mdcMap, params, "session_id");
        copyIfPresent(mdcMap, params, "job_id");
        copyIfPresent(mdcMap, params, "pid");
        Map<String, Object> unsafeParams = new LinkedHashMap<>();

        Object[] args = event.getArgumentArray();
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof Arg<?> logArg) {
                    if (arg instanceof SafeArg<?>) {
                        params.put(logArg.getName(), logArg.getValue());
                    } else {
                        unsafeParams.put(logArg.getName(), logArg.getValue());
                    }
                }
            }
        }

        envelope.put("params", params);
        envelope.put("unsafeParams", unsafeParams);
        envelope.put("tags", Map.of());

        try {
            return MAPPER.writeValueAsString(envelope) + "\n";
        } catch (JsonProcessingException e) {
            return "{\"type\":\"service.1\",\"level\":\"ERROR\",\"message\":\"Failed to serialize log event: "
                    + e.getMessage() + "\"}\n";
        }
    }

    private static void copyIfPresent(Map<String, String> source, Map<String, Object> target, String key) {
        String value = source.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }
}
