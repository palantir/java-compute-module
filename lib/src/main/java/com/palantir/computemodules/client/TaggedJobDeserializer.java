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
package com.palantir.computemodules.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.util.Optional;

public final class TaggedJobDeserializer {
    private static final SafeLogger log = SafeLoggerFactory.get(TaggedJobDeserializer.class);
    private static final ObjectMapper mapper = new ObjectMapper()
            // ignore unknown fields
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public Optional<ComputeModuleJob> deserialize(String raw) {
        try {
            ComputeModuleJob job = mapper.readValue(raw, ComputeModuleJob.class);
            return Optional.of(job);
        } catch (Exception e) {
            log.error("Failed to deserialize job", SafeArg.of("raw", raw), e);
            return Optional.empty();
        }
    }
}
