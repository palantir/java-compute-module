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
package com.palantir.computemodules.functions;

import com.palantir.computemodules.functions.results.Failed;
import com.palantir.computemodules.functions.results.Ok;
import com.palantir.computemodules.functions.results.Result;
import com.palantir.computemodules.functions.serde.Deserializer;
import com.palantir.computemodules.functions.serde.Serializer;
import java.io.InputStream;

public final class FunctionRunner<I, O> {
    private final Function<I, O> function;
    private final Class<I> inputType;
    private final Class<O> outputType;
    private final Deserializer<I> deserializer;
    private final Serializer<O> serializer;

    public FunctionRunner(
            Function<I, O> function,
            Class<I> inputType,
            Class<O> outputType,
            Deserializer<I> deserializer,
            Serializer<O> serializer) {
        this.function = function;
        this.inputType = inputType;
        this.outputType = outputType;
        this.deserializer = deserializer;
        this.serializer = serializer;
    }

    public Result run(Context context, Object input) {
        I deserializedInput = deserializer.deserialize(input, inputType);
        try {
            O result = function.run(context, deserializedInput);
            if (InputStream.class.isAssignableFrom(outputType)) {
                return new Ok(context.jobId(), (InputStream) result);
            }
            return serializer.serialize(context.jobId(), result);
        } catch (Exception e) {
            return new Failed(context.jobId(), e);
        }
    }
}
