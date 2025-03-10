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

import com.fasterxml.jackson.databind.ObjectMapper;

public final class DefaultDeserializer<I> implements Deserializer<I> {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public I deserialize(Object input, Class<I> typeMarker) {
        return mapper.convertValue(input, typeMarker);
    }
}
