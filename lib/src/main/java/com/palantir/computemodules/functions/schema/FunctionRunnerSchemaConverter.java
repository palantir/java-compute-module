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

package com.palantir.computemodules.functions.schema;

import com.palantir.computemodules.functions.FunctionRunner;
import com.palantir.computemodules.functions.api.AnonymousCustomType;
import com.palantir.computemodules.functions.api.BooleanType;
import com.palantir.computemodules.functions.api.ByteType;
import com.palantir.computemodules.functions.api.CustomTypeFieldName;
import com.palantir.computemodules.functions.api.DataType;
import com.palantir.computemodules.functions.api.DateType;
import com.palantir.computemodules.functions.api.DoubleType;
import com.palantir.computemodules.functions.api.FloatType;
import com.palantir.computemodules.functions.api.FunctionInputType;
import com.palantir.computemodules.functions.api.FunctionOutputType;
import com.palantir.computemodules.functions.api.FunctionRunnerSchema;
import com.palantir.computemodules.functions.api.FunctionRunnerSchemaParseIssue;
import com.palantir.computemodules.functions.api.InputName;
import com.palantir.computemodules.functions.api.IntegerType;
import com.palantir.computemodules.functions.api.ListType;
import com.palantir.computemodules.functions.api.LongType;
import com.palantir.computemodules.functions.api.MapType;
import com.palantir.computemodules.functions.api.OptionalType;
import com.palantir.computemodules.functions.api.SetType;
import com.palantir.computemodules.functions.api.ShortType;
import com.palantir.computemodules.functions.api.SingleOutputType;
import com.palantir.computemodules.functions.api.StringType;
import com.palantir.computemodules.functions.api.TimestampType;
import com.palantir.computemodules.functions.api.UnknownType;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import one.util.streamex.EntryStream;

/**
 * This utility class handles conversion from a Java {@link Class} to a
 * {@link DataType} object.
 */
public final class FunctionRunnerSchemaConverter {
    private static final SafeLogger log = SafeLoggerFactory.get(FunctionRunnerSchemaConverter.class);
    private static final Set<Class<?>> WRAPPER_TYPES = getWrapperTypes();
    private static final int MAX_OBJECT_DEPTH = 10;

    private FunctionRunnerSchemaConverter() {}

    public static List<FunctionRunnerSchema> getFunctionSchemas(Map<String, FunctionRunner<?, ?>> functions) {
        return EntryStream.of(functions)
                .map(function -> {
                    FunctionRunner<?, ?> functionRunner = function.getValue();
                    Optional<Map<String, DataType>> maybeInputTypes = classToDataTypes(functionRunner.inputClass());
                    Optional<DataType> maybeOutputType = classToDataType(functionRunner.outputClass());
                    List<FunctionRunnerSchemaParseIssue> issues = new ArrayList<>();
                    List<FunctionInputType> inputTypes;
                    if (maybeInputTypes.isPresent()) {
                        inputTypes = maybeInputTypes.get().entrySet().stream()
                                .map(entry -> FunctionInputType.builder()
                                        .name(InputName.valueOf(entry.getKey()))
                                        .dataType(entry.getValue())
                                        .required(true)
                                        .build())
                                .toList();
                    } else {
                        issues.add(FunctionRunnerSchemaParseIssue.CANNOT_PARSE_INPUTS);
                        inputTypes = new ArrayList<>();
                    }
                    FunctionOutputType outputType;
                    if (maybeOutputType.isPresent()) {
                        outputType = FunctionOutputType.single(SingleOutputType.builder()
                                .dataType(maybeOutputType.get())
                                .build());
                    } else {
                        issues.add(FunctionRunnerSchemaParseIssue.CANNOT_PARSE_OUTPUT);
                        outputType = FunctionOutputType.single(SingleOutputType.builder()
                                .dataType(DataType.unknown("unknown", UnknownType.of()))
                                .build());
                    }
                    return FunctionRunnerSchema.builder()
                            .functionName(function.getKey())
                            .output(outputType)
                            .addAllInputs(inputTypes)
                            .issues(issues)
                            .build();
                })
                .toList();
    }

    /**
     * Converts a Java {@link Class} to an Optional Map where each key represents a field name, and
     * each value is a {@link DataType} corresponding to the class represented by the original field.
     * This is used to generate a valid Foundry Function schema, since the input(s) of a Function are represented
     * as a list of {@link com.palantir.computemodules.functions.api.FunctionInputType} objects.
     * If the {@link Class} provided is not a complex class, this function will return `Optional.empty()` since there
     * are no fields to inspect.
     *
     * @param clazz the {@link Class} reference to inspect.
     * @return a `Map` from fieldName -> {@link DataType} for each field of the provided {@link Class}
     */
    static Optional<Map<String, DataType>> classToDataTypes(Class<?> clazz) {
        if (isAtomicType(clazz)) {
            return Optional.empty();
        }
        Map<String, DataType> fieldTypes = new HashMap<>();
        getFieldsToTraverse(clazz).forEach(field -> {
            Optional<DataType> fieldType = fieldToDataType(field);
            if (fieldType.isPresent()) {
                fieldTypes.put(field.getName(), fieldType.get());
            } else {
                fieldTypes.put(
                        field.getName(),
                        DataType.unknown(field.getType().getSimpleName().toUpperCase(Locale.ROOT), Optional.empty()));
            }
        });
        return Optional.of(fieldTypes);
    }

    /**
     * Converts a Java {@link Class} to a {@link DataType} corresponding to the class represented by the input.
     *
     * @param clazz the {@link Class} reference to inspect.
     * @return a {@link DataType} corresponding to the class provided
     */
    static Optional<DataType> classToDataType(Class<?> clazz) {
        return recClassToDataType(clazz, 0, new HashSet<>(), new Type[0]);
    }

    private static Optional<DataType> fieldToDataType(Field field) {
        Type[] typeArgs = getTypeArgumentsFromField(field);
        return recClassToDataType(field.getType(), 0, new HashSet<>(), typeArgs);
    }

    /**
     * Determines if the given class is an "atomic" (non-complex) type.
     * This check is needed because Foundry requires a function to take a complex type as input.
     *
     * @param clazz the class definition to inspect
     * @return true if the type is not a complex type (excluding built-in object types)
     */
    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private static boolean isAtomicType(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            return true;
        } else if (clazz.isArray()) {
            return true;
        } else if (clazz.isEnum()) {
            return true;
        } else if (Map.class.isAssignableFrom(clazz)) {
            return true;
        } else if (Set.class.isAssignableFrom(clazz)) {
            return true;
        } else if (List.class.isAssignableFrom(clazz)) {
            return true;
        }
        return WRAPPER_TYPES.contains(clazz);
    }

    private static Stream<Field> getFieldsToTraverse(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(field -> !field.isSynthetic() && !Modifier.isStatic(field.getModifiers()));
    }

    private static Set<Class<?>> getWrapperTypes() {
        Set<Class<?>> ret = new HashSet<>();
        ret.add(Integer.class);
        ret.add(Long.class);
        ret.add(Double.class);
        ret.add(Float.class);
        ret.add(Boolean.class);
        ret.add(String.class);
        ret.add(Date.class);
        ret.add(Byte.class);
        ret.add(Short.class);
        ret.add(Timestamp.class);
        ret.add(Character.class);
        ret.add(Void.class);
        return ret;
    }

    /**
     * Returns the parameterized types of the underlying {@link Class} of a field if that
     * underlying {@link Class} is a generic type.
     * If the underlying {@link Class} is not generic, this method will return an empty Array.
     *
     * @param field a field of a complex {@link Class}
     * @return an Array of {@link Type} references corresponding to the generic parameters of the field's underlying type
     */
    private static Type[] getTypeArgumentsFromField(Field field) {
        Type fieldGenericType = field.getGenericType();
        TypeVariable<?>[] typeParams = field.getType().getTypeParameters();
        if (typeParams.length > 0 && fieldGenericType instanceof ParameterizedType parameterizedType) {
            return parameterizedType.getActualTypeArguments();
        }
        return new Type[0];
    }

    /**
     * Extracts the raw {@link Class} from a {@link Type}, handling {@link ParameterizedType}.
     */
    private static Class<?> getRawClass(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        } else if (type instanceof ParameterizedType parameterizedType) {
            return (Class<?>) parameterizedType.getRawType();
        }
        return Object.class;
    }

    /**
     * Gets the type arguments from a {@link Type} if it's a {@link ParameterizedType}.
     */
    private static Type[] getNestedTypeArgs(Type type) {
        if (type instanceof ParameterizedType parameterizedType) {
            return parameterizedType.getActualTypeArguments();
        }
        return new Type[0];
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private static Optional<DataType> recClassToDataType(
            Class<?> clazz, int depth, Set<Class<?>> visitedCustomTypes, Type[] typeArguments) {
        // TODO(.): handle binary input
        if (depth >= MAX_OBJECT_DEPTH) {
            log.error(
                    "Java class too deeply nested to be converted to a DataType",
                    SafeArg.of("className", clazz.getTypeName()),
                    SafeArg.of("maxDepth", MAX_OBJECT_DEPTH));
            return Optional.empty();
        } else if (clazz == Integer.class || clazz == int.class) {
            return Optional.of(DataType.integer(IntegerType.of()));
        } else if (clazz == Long.class || clazz == long.class) {
            return Optional.of(DataType.long_(LongType.of()));
        } else if (clazz == Double.class || clazz == double.class) {
            return Optional.of(DataType.double_(DoubleType.of()));
        } else if (clazz == Float.class || clazz == float.class) {
            return Optional.of(DataType.float_(FloatType.of()));
        } else if (clazz == Boolean.class || clazz == boolean.class) {
            return Optional.of(DataType.boolean_(BooleanType.of()));
        } else if (clazz == String.class) {
            return Optional.of(DataType.string(StringType.of()));
        } else if (clazz == Date.class) {
            return Optional.of(DataType.date(DateType.of()));
        } else if (clazz == Byte.class || clazz == byte.class) {
            return Optional.of(DataType.byte_(ByteType.of()));
        } else if (clazz == Short.class || clazz == short.class) {
            return Optional.of(DataType.short_(ShortType.of()));
        } else if (clazz == Timestamp.class) {
            return Optional.of(DataType.timestamp(TimestampType.of()));
        } else if (clazz.isArray()) {
            Optional<DataType> componentType =
                    recClassToDataType(clazz.getComponentType(), depth + 1, visitedCustomTypes, new Type[0]);
            return componentType.map(dataType -> DataType.list(ListType.of(dataType)));
        } else if (Map.class.isAssignableFrom(clazz)) {
            Optional<DataType> maybeKeyType = Optional.empty();
            Optional<DataType> maybeValueType = Optional.empty();
            if (typeArguments.length == 2) {
                maybeKeyType = recClassToDataType(
                        getRawClass(typeArguments[0]),
                        depth + 1,
                        visitedCustomTypes,
                        getNestedTypeArgs(typeArguments[0]));
                maybeValueType = recClassToDataType(
                        getRawClass(typeArguments[1]),
                        depth + 1,
                        visitedCustomTypes,
                        getNestedTypeArgs(typeArguments[1]));
            }
            DataType keyType = maybeKeyType.orElseGet(() -> DataType.unknown("key", Optional.empty()));
            DataType valueType = maybeValueType.orElseGet(() -> DataType.unknown("value", Optional.empty()));
            return Optional.of(DataType.map(MapType.of(keyType, valueType)));
        } else if (clazz == Optional.class) {
            Optional<DataType> maybeParamType = Optional.empty();
            if (typeArguments.length > 0) {
                maybeParamType = recClassToDataType(
                        getRawClass(typeArguments[0]),
                        depth + 1,
                        visitedCustomTypes,
                        getNestedTypeArgs(typeArguments[0]));
            }
            DataType paramType = maybeParamType.orElseGet(() -> DataType.unknown("value", Optional.empty()));
            return Optional.of(DataType.optionalType(OptionalType.of(paramType)));
        } else if (List.class.isAssignableFrom(clazz)) {
            Optional<DataType> maybeParamType = Optional.empty();
            if (typeArguments.length > 0) {
                maybeParamType = recClassToDataType(
                        getRawClass(typeArguments[0]),
                        depth + 1,
                        visitedCustomTypes,
                        getNestedTypeArgs(typeArguments[0]));
            }
            DataType paramType = maybeParamType.orElseGet(() -> DataType.unknown("value", Optional.empty()));
            return Optional.of(DataType.list(ListType.of(paramType)));
        } else if (Set.class.isAssignableFrom(clazz)) {
            Optional<DataType> maybeParamType = Optional.empty();
            if (typeArguments.length > 0) {
                maybeParamType = recClassToDataType(
                        getRawClass(typeArguments[0]),
                        depth + 1,
                        visitedCustomTypes,
                        getNestedTypeArgs(typeArguments[0]));
            }
            DataType paramType = maybeParamType.orElseGet(() -> DataType.unknown("value", Optional.empty()));
            return Optional.of(DataType.set(SetType.of(paramType)));
        } else {
            if (visitedCustomTypes.contains(clazz)) {
                log.error("Detected cyclic DataType", SafeArg.of("className", clazz.getTypeName()));
                return Optional.empty();
            }
            Set<Class<?>> nextVisited = new HashSet<>(visitedCustomTypes);
            nextVisited.add(clazz);
            Map<CustomTypeFieldName, DataType> fieldsAsDataType = new HashMap<>();
            getFieldsToTraverse(clazz).forEach(field -> {
                CustomTypeFieldName fName = CustomTypeFieldName.of(field.getName());
                Type[] typeArgs = getTypeArgumentsFromField(field);
                Optional<DataType> dataType = recClassToDataType(field.getType(), depth + 1, nextVisited, typeArgs);
                if (dataType.isPresent()) {
                    fieldsAsDataType.put(fName, dataType.get());
                } else {
                    log.error(
                            "A member of this Java class was unable to be converted to a DataType.",
                            SafeArg.of("className", clazz.getTypeName()),
                            SafeArg.of("fieldName", field.getName()),
                            SafeArg.of("childClassName", field.getType().getTypeName()));
                    // Casting fieldType toUpperCase to avoid `provided type is known` error
                    fieldsAsDataType.put(
                            fName,
                            DataType.unknown(
                                    field.getType().getSimpleName().toUpperCase(Locale.ROOT), Optional.empty()));
                }
            });
            return Optional.of(DataType.anonymousCustomType(
                    AnonymousCustomType.builder().fields(fieldsAsDataType).build()));
        }
    }
}
