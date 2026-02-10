/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.computemodules.auth;

import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.logsafe.exceptions.SafeUncheckedIoException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class PipelinesAuth {
    private PipelinesAuth() {}

    // Produces a bearer token that can be used to make calls to access pipeline resources.
    // This is only available in pipeline mode.
    public static String retrievePipelineToken() {
        // TODO(.): Replace with EnvVars.Reserved
        String tokenFilePath = System.getenv("BUILD2_TOKEN");
        if (tokenFilePath == null || tokenFilePath.isEmpty()) {
            throw new SafeRuntimeException(
                    "Pipeline token not available. Please make sure you are running in Pipeline mode.");
        }
        try {
            return Files.readString(Paths.get(tokenFilePath), StandardCharsets.UTF_8)
                    .trim();
        } catch (IOException e) {
            throw new SafeUncheckedIoException("Failed to read pipeline token from file where it is stored.", e);
        }
    }
}
