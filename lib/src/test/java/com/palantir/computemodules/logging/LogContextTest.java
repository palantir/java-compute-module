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
package com.palantir.computemodules.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class LogContextTest {

    @AfterEach
    void after() {
        MDC.clear();
    }

    @Test
    void initThread_sets_session_id_in_mdc() {
        LogContext.initThread();
        assertThat(MDC.get("session_id")).isNotNull();
    }

    @Test
    void setJobId_puts_job_id_in_mdc() {
        LogContext.setJobId("job-123");
        assertThat(MDC.get("job_id")).isEqualTo("job-123");
    }

    @Test
    void clearJobId_removes_job_id_from_mdc() {
        LogContext.setJobId("job-123");
        LogContext.clearJobId();
        assertThat(MDC.get("job_id")).isNull();
    }

    @Test
    void sessionId_returns_consistent_value() {
        String id1 = LogContext.sessionId();
        String id2 = LogContext.sessionId();
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void mdc_is_thread_isolated() throws Exception {
        LogContext.initThread();
        LogContext.setJobId("main-job");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> otherThreadJobId = new AtomicReference<>();

        Thread thread = new Thread(() -> {
            otherThreadJobId.set(MDC.get("job_id"));
            latch.countDown();
        });
        thread.start();
        latch.await();

        assertThat(MDC.get("job_id")).isEqualTo("main-job");
        assertThat(otherThreadJobId.get()).isNull();
    }

    @Test
    void initThread_on_new_thread_sets_session_id_independently() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> otherThreadSessionId = new AtomicReference<>();

        Thread thread = new Thread(() -> {
            LogContext.initThread();
            otherThreadSessionId.set(MDC.get("session_id"));
            latch.countDown();
        });
        thread.start();
        latch.await();

        assertThat(otherThreadSessionId.get()).isEqualTo(LogContext.sessionId());
    }
}
