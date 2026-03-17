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

import org.slf4j.MDC;

public final class LogContext {

    private static final String SESSION_ID_KEY = "session_id";
    private static final String JOB_ID_KEY = "job_id";
    private static final String PID_KEY = "pid";
    private static final String SESSION_ID = resolveSessionId();
    private static final String PID = String.valueOf(ProcessHandle.current().pid());

    private LogContext() {}

    public static void initThread() {
        MDC.put(SESSION_ID_KEY, SESSION_ID);
        MDC.put(PID_KEY, PID);
    }

    public static void setJobId(String jobId) {
        MDC.put(JOB_ID_KEY, jobId);
    }

    public static void clearJobId() {
        MDC.remove(JOB_ID_KEY);
    }

    public static String sessionId() {
        return SESSION_ID;
    }

    private static String resolveSessionId() {
        String value = System.getenv("COMPUTE_SESSION_ID");
        return value != null ? value : "";
    }
}
