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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palantir.computemodules.functions.results.Failed;
import com.palantir.computemodules.functions.results.Ok;
import com.palantir.computemodules.functions.results.Result;
import java.io.ByteArrayInputStream;

public final class DefaultSerializer<O> implements Serializer<O> {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Result serialize(String jobId, O output) {
        try {
            return new Ok(jobId, new ByteArrayInputStream(mapper.writeValueAsBytes(output)));
        } catch (JsonProcessingException exception) {
            return new Failed(jobId, exception);
        }
    }
}
