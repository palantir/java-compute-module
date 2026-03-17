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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import org.slf4j.LoggerFactory;

/** Programmatic Logback configuration for compute modules. */
public final class LoggingConfiguration {

    private LoggingConfiguration() {}

    /** Configures stdout logging at INFO level using the provided layout. */
    public static void configure(Layout<ILoggingEvent> layout) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        layout.setContext(context);
        layout.start();

        LayoutWrappingEncoder<ILoggingEvent> encoder = new LayoutWrappingEncoder<>();
        encoder.setContext(context);
        encoder.setLayout(layout);

        ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<>();
        appender.setContext(context);
        appender.setTarget("System.out");
        appender.setEncoder(encoder);
        appender.start();

        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(appender);
        root.setLevel(Level.INFO);
    }
}
