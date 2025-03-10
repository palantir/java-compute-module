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
package com.palantir.computemodules.client.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class EnvVars {
    public enum Reserved {
        GET_JOB_URI(StringEnvVar.of("GET_JOB_URI")),
        POST_RESULT_URI(StringEnvVar.of("POST_RESULT_URI")),
        DEFAULT_CA_PATH(StringEnvVar.of("DEFAULT_CA_PATH")),
        MODULE_AUTH_TOKEN(FileEnvVar.of("MODULE_AUTH_TOKEN")),
        ;

        public String get() {
            return this.type.get();
        }

        private final EnvVarType type;

        Reserved(EnvVarType type) {
            this.type = type;
        }
    }

    private EnvVars() {}

    @com.google.errorprone.annotations.Immutable
    private sealed interface EnvVarType permits StringEnvVar, FileEnvVar {
        String get();
    }

    private record StringEnvVar(String name) implements EnvVarType {

        private static StringEnvVar of(String name) {
            return new StringEnvVar(name);
        }

        @Override
        public String get() {
            return readAsString(name);
        }
    }

    private record FileEnvVar(String name) implements EnvVarType {

        private static FileEnvVar of(String name) {
            return new FileEnvVar(name);
        }

        @Override
        public String get() {
            try {
                return Files.readString(Path.of(readAsString(name)));
            } catch (IOException e) {
                throw new RuntimeException("Failed to read file pointed to by EnvVar: " + name, e);
            }
        }
    }

    private static String readAsString(String envVar) {
        return Optional.ofNullable(System.getenv(envVar))
                .orElseThrow(() -> new RuntimeException("Required environment variable not found: " + envVar));
    }
}
