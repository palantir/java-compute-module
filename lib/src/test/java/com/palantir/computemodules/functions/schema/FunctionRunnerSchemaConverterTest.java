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

import static org.assertj.core.api.Assertions.assertThat;

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
import com.palantir.computemodules.functions.api.IntegerType;
import com.palantir.computemodules.functions.api.ListType;
import com.palantir.computemodules.functions.api.LongType;
import com.palantir.computemodules.functions.api.MapType;
import com.palantir.computemodules.functions.api.OptionalType;
import com.palantir.computemodules.functions.api.SetType;
import com.palantir.computemodules.functions.api.ShortType;
import com.palantir.computemodules.functions.api.StringType;
import com.palantir.computemodules.functions.serde.Deserializer;
import com.palantir.computemodules.functions.serde.Serializer;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("JavaUtilDate") // Testing Date type handling in schema converter
class FunctionRunnerSchemaConverterTest {
    static class ChildTestClass {
        public final boolean childFieldOne;
        public final Double childFieldTwo;
        public final Date[] childFieldThree;
        static ChildTestClass shouldbeIgnored = new ChildTestClass(true, 2.0, new Date[] {new Date()});

        ChildTestClass(boolean childFieldOne, Double childFieldTwo, Date[] childFieldThree) {
            this.childFieldOne = childFieldOne;
            this.childFieldTwo = childFieldTwo;
            this.childFieldThree = childFieldThree;
        }
    }

    static class ParentTestClass {
        private final int parentFieldOne;
        private final String parentFieldTwo;
        private final ChildTestClass child;

        ParentTestClass(int parentFieldOne, String parentFieldTwo, ChildTestClass child) {
            this.parentFieldOne = parentFieldOne;
            this.parentFieldTwo = parentFieldTwo;
            this.child = child;
        }

        public int getParentFieldOne() {
            return parentFieldOne;
        }

        public String getParentFieldTwo() {
            return parentFieldTwo;
        }

        public ChildTestClass getChild() {
            return child;
        }
    }

    static class RecursiveTestClass {
        public final RecursiveTestClass child;

        RecursiveTestClass(RecursiveTestClass child) {
            this.child = child;
        }
    }

    record TestRecord(
            long fieldOne, float fieldTwo, byte fieldThree, short fieldFour, SortedMap<String, String> fieldFive) {}

    static class TestGenerics {
        public final boolean nonGenericField;
        public final List<Integer> intList;
        public final Optional<String> optionalString;
        public final Map<String, Date> stringMap;
        public final Set<Byte> byteSet;

        TestGenerics(
                boolean nonGenericField,
                List<Integer> intList,
                Optional<String> optionalString,
                Map<String, Date> stringMap,
                Set<Byte> byteSet) {
            this.intList = intList;
            this.optionalString = optionalString;
            this.nonGenericField = nonGenericField;
            this.stringMap = stringMap;
            this.byteSet = byteSet;
        }
    }

    static class TestNestedGenerics {
        public final List<Map<String, String>> rows;

        TestNestedGenerics(List<Map<String, String>> rows) {
            this.rows = rows;
        }
    }

    record Detail(String name) {}

    record Item(String id, List<Detail> details) {}

    record Request(List<Item> items) {}

    record Inner(List<String> leaves) {}

    static class ListWrapper {
        public final List<Inner> items;

        ListWrapper(List<Inner> items) {
            this.items = items;
        }
    }

    private static DataType expectedChildTestClassOutput() {
        Map<CustomTypeFieldName, DataType> childFields = new HashMap<>();
        childFields.put(CustomTypeFieldName.of("childFieldOne"), DataType.boolean_(BooleanType.of()));
        childFields.put(CustomTypeFieldName.of("childFieldTwo"), DataType.double_(DoubleType.of()));
        childFields.put(
                CustomTypeFieldName.of("childFieldThree"), DataType.list(ListType.of(DataType.date(DateType.of()))));
        return DataType.anonymousCustomType(
                AnonymousCustomType.builder().fields(childFields).build());
    }

    private static DataType getExpectedOutput() {
        Map<CustomTypeFieldName, DataType> parentFields = new HashMap<>();
        parentFields.put(CustomTypeFieldName.of("parentFieldOne"), DataType.integer(IntegerType.of()));
        parentFields.put(CustomTypeFieldName.of("parentFieldTwo"), DataType.string(StringType.of()));
        parentFields.put(CustomTypeFieldName.of("child"), expectedChildTestClassOutput());
        return DataType.anonymousCustomType(
                AnonymousCustomType.builder().fields(parentFields).build());
    }

    private static DataType getExpectedRecordOutput() {
        Map<CustomTypeFieldName, DataType> typeFields = new HashMap<>();
        typeFields.put(CustomTypeFieldName.of("fieldOne"), DataType.long_(LongType.of()));
        typeFields.put(CustomTypeFieldName.of("fieldTwo"), DataType.float_(FloatType.of()));
        typeFields.put(CustomTypeFieldName.of("fieldThree"), DataType.byte_(ByteType.of()));
        typeFields.put(CustomTypeFieldName.of("fieldFour"), DataType.short_(ShortType.of()));
        typeFields.put(
                CustomTypeFieldName.of("fieldFive"),
                DataType.map(MapType.of(DataType.string(StringType.of()), DataType.string(StringType.of()))));
        return DataType.anonymousCustomType(
                AnonymousCustomType.builder().fields(typeFields).build());
    }

    private static DataType getExpectedRecursiveOutput() {
        Map<CustomTypeFieldName, DataType> typeFields = new HashMap<>();
        typeFields.put(CustomTypeFieldName.of("child"), DataType.unknown("RECURSIVETESTCLASS", Optional.empty()));
        return DataType.anonymousCustomType(
                AnonymousCustomType.builder().fields(typeFields).build());
    }

    private static DataType getExpectedGenericOutput() {
        Map<CustomTypeFieldName, DataType> typeFields = new HashMap<>();
        typeFields.put(CustomTypeFieldName.of("nonGenericField"), DataType.boolean_(BooleanType.of()));
        typeFields.put(
                CustomTypeFieldName.of("intList"), DataType.list(ListType.of(DataType.integer(IntegerType.of()))));
        typeFields.put(
                CustomTypeFieldName.of("optionalString"),
                DataType.optionalType(OptionalType.of(DataType.string(StringType.of()))));
        typeFields.put(
                CustomTypeFieldName.of("stringMap"),
                DataType.map(MapType.of(DataType.string(StringType.of()), DataType.date(DateType.of()))));
        typeFields.put(CustomTypeFieldName.of("byteSet"), DataType.set(SetType.of(DataType.byte_(ByteType.of()))));
        return DataType.anonymousCustomType(
                AnonymousCustomType.builder().fields(typeFields).build());
    }

    @Test
    public void testClassToDataType() {
        ChildTestClass child = new ChildTestClass(false, 20.0, new Date[] {new Date()});
        ParentTestClass parent = new ParentTestClass(0, "parentFieldOne", child);
        Optional<DataType> dType = FunctionRunnerSchemaConverter.classToDataType(parent.getClass());
        assertThat(dType).isNotEmpty();
        assertThat(dType.orElseThrow()).isEqualTo(getExpectedOutput());
    }

    @Test
    public void testClassToDataTypes() {
        ChildTestClass child = new ChildTestClass(false, 20.0, new Date[] {new Date()});
        ParentTestClass parent = new ParentTestClass(0, "parentFieldOne", child);
        Optional<Map<String, DataType>> dTypes = FunctionRunnerSchemaConverter.classToDataTypes(parent.getClass());
        assertThat(dTypes).isNotEmpty();
        assertThat(dTypes.orElseThrow()).hasSize(3);
        assertThat(dTypes.orElseThrow().keySet())
                .containsExactlyInAnyOrderElementsOf(
                        new HashSet<>(Arrays.asList("parentFieldOne", "parentFieldTwo", "child")));
        assertThat(dTypes.orElseThrow().get("parentFieldOne")).isEqualTo(DataType.integer(IntegerType.of()));
        assertThat(dTypes.orElseThrow().get("parentFieldTwo")).isEqualTo(DataType.string(StringType.of()));
        assertThat(dTypes.orElseThrow().get("child")).isEqualTo(expectedChildTestClassOutput());
    }

    @Test
    public void testRecordToDataType() {
        Optional<DataType> dType = FunctionRunnerSchemaConverter.classToDataType(TestRecord.class);
        assertThat(dType).isNotEmpty();
        assertThat(dType.orElseThrow()).isEqualTo(getExpectedRecordOutput());
    }

    @Test
    public void testGenericsToDataType() {
        Optional<DataType> dType = FunctionRunnerSchemaConverter.classToDataType(TestGenerics.class);
        assertThat(dType).isNotEmpty();
        assertThat(dType.orElseThrow()).isEqualTo(getExpectedGenericOutput());
    }

    @Test
    public void testSimpleClassToDataType() {
        Optional<DataType> dType = FunctionRunnerSchemaConverter.classToDataType(Integer.class);
        assertThat(dType.orElseThrow()).isEqualTo(DataType.integer(IntegerType.of()));

        dType = FunctionRunnerSchemaConverter.classToDataType(int.class);
        assertThat(dType.orElseThrow()).isEqualTo(DataType.integer(IntegerType.of()));

        dType = FunctionRunnerSchemaConverter.classToDataType(Long.class);
        assertThat(dType.orElseThrow()).isEqualTo(DataType.long_(LongType.of()));

        dType = FunctionRunnerSchemaConverter.classToDataType(long.class);
        assertThat(dType.orElseThrow()).isEqualTo(DataType.long_(LongType.of()));

        dType = FunctionRunnerSchemaConverter.classToDataType(Double.class);
        assertThat(dType.orElseThrow()).isEqualTo(DataType.double_(DoubleType.of()));

        dType = FunctionRunnerSchemaConverter.classToDataType(double.class);
        assertThat(dType.orElseThrow()).isEqualTo(DataType.double_(DoubleType.of()));

        dType = FunctionRunnerSchemaConverter.classToDataType(Float.class);
        assertThat(dType.orElseThrow()).isEqualTo(DataType.float_(FloatType.of()));

        dType = FunctionRunnerSchemaConverter.classToDataType(float.class);
        assertThat(dType.orElseThrow()).isEqualTo(DataType.float_(FloatType.of()));

        dType = FunctionRunnerSchemaConverter.classToDataType(Boolean.class);
        assertThat(dType.orElseThrow()).isEqualTo(DataType.boolean_(BooleanType.of()));

        dType = FunctionRunnerSchemaConverter.classToDataType(float.class);
        assertThat(dType.orElseThrow()).isEqualTo(DataType.float_(FloatType.of()));

        dType = FunctionRunnerSchemaConverter.classToDataType(String.class);
        assertThat(dType.orElseThrow()).isEqualTo(DataType.string(StringType.of()));

        dType = FunctionRunnerSchemaConverter.classToDataType(Date.class);
        assertThat(dType.orElseThrow()).isEqualTo(DataType.date(DateType.of()));

        dType = FunctionRunnerSchemaConverter.classToDataType(Optional.class);
        assertThat(dType.orElseThrow())
                .isEqualTo(DataType.optionalType(OptionalType.of(DataType.unknown("value", Optional.empty()))));
    }

    @Test
    public void testRecursiveClassToDataType() {
        Optional<DataType> dType = FunctionRunnerSchemaConverter.classToDataType(RecursiveTestClass.class);
        assertThat(dType).isNotEmpty();
        assertThat(dType.orElseThrow()).isEqualTo(getExpectedRecursiveOutput());
    }

    @Test
    public void testNonArrayCollectionFailure() {
        Optional<DataType> dType = FunctionRunnerSchemaConverter.classToDataType(List.class);
        assertThat(dType.orElseThrow())
                .isEqualTo(DataType.list(ListType.of(DataType.unknown("value", Optional.empty()))));
    }

    @Test
    public void testNestedGenericsToDataType() {
        Optional<DataType> dType = FunctionRunnerSchemaConverter.classToDataType(TestNestedGenerics.class);
        assertThat(dType).isNotEmpty();
        Map<CustomTypeFieldName, DataType> expectedFields = new HashMap<>();
        expectedFields.put(
                CustomTypeFieldName.of("rows"),
                DataType.list(ListType.of(
                        DataType.map(MapType.of(DataType.string(StringType.of()), DataType.string(StringType.of()))))));
        DataType expected = DataType.anonymousCustomType(
                AnonymousCustomType.builder().fields(expectedFields).build());
        assertThat(dType.orElseThrow()).isEqualTo(expected);
    }

    @Test
    public void testRecordWithNestedListOfRecordsContainingList() {
        Optional<DataType> dType = FunctionRunnerSchemaConverter.classToDataType(Request.class);
        assertThat(dType).isNotEmpty();

        Map<CustomTypeFieldName, DataType> detailFields = new HashMap<>();
        detailFields.put(CustomTypeFieldName.of("name"), DataType.string(StringType.of()));
        DataType detailType = DataType.anonymousCustomType(
                AnonymousCustomType.builder().fields(detailFields).build());

        Map<CustomTypeFieldName, DataType> itemFields = new HashMap<>();
        itemFields.put(CustomTypeFieldName.of("id"), DataType.string(StringType.of()));
        itemFields.put(CustomTypeFieldName.of("details"), DataType.list(ListType.of(detailType)));
        DataType itemType = DataType.anonymousCustomType(
                AnonymousCustomType.builder().fields(itemFields).build());

        Map<CustomTypeFieldName, DataType> requestFields = new HashMap<>();
        requestFields.put(CustomTypeFieldName.of("items"), DataType.list(ListType.of(itemType)));
        DataType expected = DataType.anonymousCustomType(
                AnonymousCustomType.builder().fields(requestFields).build());

        assertThat(dType.orElseThrow()).isEqualTo(expected);
    }

    @Test
    public void testTopLevelListFieldContainingNestedList() {
        Optional<Map<String, DataType>> dTypes = FunctionRunnerSchemaConverter.classToDataTypes(ListWrapper.class);
        assertThat(dTypes).isNotEmpty();

        Map<CustomTypeFieldName, DataType> innerFields = new HashMap<>();
        innerFields.put(CustomTypeFieldName.of("leaves"), DataType.list(ListType.of(DataType.string(StringType.of()))));
        DataType innerType = DataType.anonymousCustomType(
                AnonymousCustomType.builder().fields(innerFields).build());

        assertThat(dTypes.orElseThrow()).hasSize(1);
        assertThat(dTypes.orElseThrow().get("items")).isEqualTo(DataType.list(ListType.of(innerType)));
    }

    // Test input/output classes for getFunctionSchemas tests
    static class SimpleInput {
        public final String name;
        public final int value;

        SimpleInput(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    static class SimpleOutput {
        public final boolean success;
        public final String message;

        SimpleOutput(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    @SuppressWarnings("unchecked")
    private static <I, O> FunctionRunner<I, O> createMockFunctionRunner(Class<I> inputClass, Class<O> outputClass) {
        return new FunctionRunner<>(
                (_context, _input) -> null,
                inputClass,
                outputClass,
                (Deserializer<I>) (input, _type) -> (I) input,
                (Serializer<O>) (_jobId, _output) -> null);
    }

    @Test
    public void testGetFunctionSchemasWithSingleFunction() {
        Map<String, FunctionRunner<?, ?>> functions = new HashMap<>();
        functions.put("testFunction", createMockFunctionRunner(SimpleInput.class, SimpleOutput.class));

        List<FunctionRunnerSchema> schemas = FunctionRunnerSchemaConverter.getFunctionSchemas(functions);

        assertThat(schemas).hasSize(1);
        FunctionRunnerSchema schema = schemas.get(0);
        assertThat(schema.getFunctionName()).isEqualTo("testFunction");
        assertThat(schema.getIssues()).isEmpty();

        // Verify inputs
        List<FunctionInputType> inputs = schema.getInputs();
        assertThat(inputs).hasSize(2);
        Set<String> inputNames =
                new HashSet<>(inputs.stream().map(i -> i.getName().get()).toList());
        assertThat(inputNames).containsExactlyInAnyOrder("name", "value");

        // Verify output is a single output type
        FunctionOutputType outputType = schema.getOutput();
        DataType outputDataType = outputType.accept(FunctionOutputType.Visitor.<DataType>builder()
                .single(single -> single.getDataType())
                .void_(_void -> null)
                .throwOnUnknown()
                .build());
        assertThat(outputDataType).isNotNull();
    }

    @Test
    public void testGetFunctionSchemasWithMultipleFunctions() {
        Map<String, FunctionRunner<?, ?>> functions = new HashMap<>();
        functions.put("function1", createMockFunctionRunner(SimpleInput.class, String.class));
        functions.put("function2", createMockFunctionRunner(ParentTestClass.class, Integer.class));

        List<FunctionRunnerSchema> schemas = FunctionRunnerSchemaConverter.getFunctionSchemas(functions);

        assertThat(schemas).hasSize(2);
        Set<String> functionNames = new HashSet<>(
                schemas.stream().map(FunctionRunnerSchema::getFunctionName).toList());
        assertThat(functionNames).containsExactlyInAnyOrder("function1", "function2");
    }

    @Test
    public void testGetFunctionSchemasWithEmptyMap() {
        Map<String, FunctionRunner<?, ?>> functions = new HashMap<>();

        List<FunctionRunnerSchema> schemas = FunctionRunnerSchemaConverter.getFunctionSchemas(functions);

        assertThat(schemas).isEmpty();
    }

    @Test
    public void testGetFunctionSchemasWithAtomicInputType() {
        Map<String, FunctionRunner<?, ?>> functions = new HashMap<>();
        functions.put("atomicInputFunction", createMockFunctionRunner(String.class, SimpleOutput.class));

        List<FunctionRunnerSchema> schemas = FunctionRunnerSchemaConverter.getFunctionSchemas(functions);

        assertThat(schemas).hasSize(1);
        FunctionRunnerSchema schema = schemas.get(0);
        assertThat(schema.getFunctionName()).isEqualTo("atomicInputFunction");
        assertThat(schema.getIssues()).containsExactly(FunctionRunnerSchemaParseIssue.CANNOT_PARSE_INPUTS);
        assertThat(schema.getInputs()).isEmpty();
    }

    @Test
    public void testGetFunctionSchemasInputsAreMarkedRequired() {
        Map<String, FunctionRunner<?, ?>> functions = new HashMap<>();
        functions.put("testFunction", createMockFunctionRunner(SimpleInput.class, String.class));

        List<FunctionRunnerSchema> schemas = FunctionRunnerSchemaConverter.getFunctionSchemas(functions);

        assertThat(schemas).hasSize(1);
        FunctionRunnerSchema schema = schemas.get(0);
        for (FunctionInputType input : schema.getInputs()) {
            assertThat(input.getRequired()).isTrue();
        }
    }

    @Test
    public void testGetFunctionSchemasOutputTypeCorrectlyConverted() {
        Map<String, FunctionRunner<?, ?>> functions = new HashMap<>();
        functions.put("stringOutputFunction", createMockFunctionRunner(SimpleInput.class, String.class));

        List<FunctionRunnerSchema> schemas = FunctionRunnerSchemaConverter.getFunctionSchemas(functions);

        assertThat(schemas).hasSize(1);
        FunctionRunnerSchema schema = schemas.get(0);
        FunctionOutputType outputType = schema.getOutput();
        DataType outputDataType = outputType.accept(FunctionOutputType.Visitor.<DataType>builder()
                .single(single -> single.getDataType())
                .void_(_void -> null)
                .throwOnUnknown()
                .build());
        assertThat(outputDataType).isEqualTo(DataType.string(StringType.of()));
    }

    @Test
    public void testGetFunctionSchemasWithComplexOutputType() {
        Map<String, FunctionRunner<?, ?>> functions = new HashMap<>();
        functions.put("complexOutputFunction", createMockFunctionRunner(SimpleInput.class, SimpleOutput.class));

        List<FunctionRunnerSchema> schemas = FunctionRunnerSchemaConverter.getFunctionSchemas(functions);

        assertThat(schemas).hasSize(1);
        FunctionRunnerSchema schema = schemas.get(0);
        FunctionOutputType outputType = schema.getOutput();
        DataType outputDataType = outputType.accept(FunctionOutputType.Visitor.<DataType>builder()
                .single(single -> single.getDataType())
                .void_(_void -> null)
                .throwOnUnknown()
                .build());

        // Verify output is an anonymous custom type with the expected fields
        Map<CustomTypeFieldName, DataType> expectedFields = new HashMap<>();
        expectedFields.put(CustomTypeFieldName.of("success"), DataType.boolean_(BooleanType.of()));
        expectedFields.put(CustomTypeFieldName.of("message"), DataType.string(StringType.of()));
        DataType expectedOutput = DataType.anonymousCustomType(
                AnonymousCustomType.builder().fields(expectedFields).build());
        assertThat(outputDataType).isEqualTo(expectedOutput);
    }
}
