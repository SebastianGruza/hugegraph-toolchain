/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.apache.hugegraph.loader.test.unit;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import org.apache.hugegraph.loader.builder.ElementBuilder;
import org.apache.hugegraph.loader.mapping.ElementMapping;
import org.apache.hugegraph.loader.mapping.VertexMapping;
import org.apache.hugegraph.loader.source.file.FileFormat;
import org.apache.hugegraph.loader.source.file.FileSource;
import org.apache.hugegraph.loader.util.DataTypeUtil;
import org.apache.hugegraph.loader.util.JsonUtil;
import org.apache.hugegraph.structure.schema.PropertyKey;
import org.apache.hugegraph.testutil.Assert;
import org.junit.Test;

import com.google.common.collect.ImmutableSet;

public class NullValuesTest {

    @Test
    public void testNumericNullValuesMatchByValue() {
        // JSON rows and null_values are both parsed as BigDecimal now
        ElementMapping mapping = JsonUtil.fromJson(
                "{\"id\":\"name\",\"label\":\"person\",\"null_values\":[1.0,\"\"]}", VertexMapping.class);
        Set<Object> nullValues = mapping.nullValues();
        Assert.assertTrue(nullValues.contains(new BigDecimal("1.0")));
        Map<String, Object> row = JsonUtil.fromJson("{\"a\":1.00,\"b\":1.5,\"c\":\"\"}",
                                                    Map.class);
        Assert.assertTrue(ElementBuilder.isNullValue(nullValues, row.get("a")));
        Assert.assertFalse(ElementBuilder.isNullValue(nullValues, row.get("b")));
        Assert.assertTrue(ElementBuilder.isNullValue(nullValues, row.get("c")));
        // values from other parsers (JDBC, CSV) match by value too
        Assert.assertTrue(ElementBuilder.isNullValue(nullValues, 1L));
        Assert.assertTrue(ElementBuilder.isNullValue(nullValues, 1.0d));
        Assert.assertFalse(ElementBuilder.isNullValue(nullValues, "1.0"));
        Assert.assertFalse(ElementBuilder.isNullValue(ImmutableSet.of("NULL"), 1L));
        // a JDBC NaN or infinity is not a null value unless listed itself
        Assert.assertFalse(ElementBuilder.isNullValue(nullValues, Double.NaN));
        Assert.assertFalse(ElementBuilder.isNullValue(nullValues, Float.POSITIVE_INFINITY));
        Assert.assertTrue(ElementBuilder.isNullValue(ImmutableSet.of(Double.NaN), Double.NaN));
        Assert.assertFalse(ElementBuilder.isNullValue(ImmutableSet.of(Double.NaN), 1.0d));
    }

    /** A field without a mapping entry keeps its value; a DECIMAL column of a mapped struct keeps every digit. */
    @Test
    public void testMappedStructKeepsUnmappedDecimal() {
        FileSource json = new FileSource();
        json.format(FileFormat.JSON);
        ElementMapping mapping = JsonUtil.fromJson(
                "{\"id\":\"name\",\"label\":\"person\",\"value_mapping\":{\"city\":{\"1\":\"Beijing\"}}}",
                VertexMapping.class);
        Map<String, Object> row = JsonUtil.fromJson(
                "{\"city\":1,\"amount\":12345678901234567890.10,\"ratio\":1.50}", Map.class);
        Assert.assertEquals("Beijing", DataTypeUtil.mapValue(mapping, "city", row.get("city"), json, true));
        Object amount = DataTypeUtil.mapValue(mapping, "amount", row.get("amount"), json, true);
        Assert.assertEquals(new BigDecimal("12345678901234567890.10"), amount);
        Assert.assertEquals(new BigDecimal("1.50"), DataTypeUtil.mapValue(mapping, "ratio", row.get("ratio"), json, true));
        // a mapped fraction is looked up by its double text
        ElementMapping m2 = JsonUtil.fromJson(
                "{\"id\":\"name\",\"label\":\"person\",\"value_mapping\":{\"ratio\":{\"1.5\":\"half\"}}}",
                VertexMapping.class);
        Assert.assertEquals("half", DataTypeUtil.mapValue(m2, "ratio", row.get("ratio"), json, true));
        // without any mapping the value is untouched
        ElementMapping m3 = JsonUtil.fromJson("{\"id\":\"name\",\"label\":\"person\"}", VertexMapping.class);
        Assert.assertSame(row.get("amount"), DataTypeUtil.mapValue(m3, "amount", row.get("amount"), json, true));
        // an unmapped non-decimal value next to a value_mapping is the string it was before,
        // so a JSON boolean still loads into a TEXT key
        Map<String, Object> row2 = JsonUtil.fromJson("{\"city\":1,\"active\":true}", Map.class);
        Object active = DataTypeUtil.mapValue(mapping, "active", row2.get("active"), json, true);
        Assert.assertEquals("true", active);
        PropertyKey text = new PropertyKey.BuilderImpl("active", null).asText().build();
        Assert.assertEquals("true", DataTypeUtil.convert(active, text, json));
    }

    /**
     * A fractional replacement value of a value_mapping comes from the struct's JSON as a
     * BigDecimal; on a CSV row it still gives the double text for TEXT and id outputs
     * ("1.5", as before) and every digit for a DECIMAL output.
     */
    @Test
    public void testMappedFractionOnACsvRow() {
        FileSource csv = new FileSource();
        csv.format(FileFormat.CSV);
        ElementMapping mapping = JsonUtil.fromJson(
                "{\"id\":\"name\",\"label\":\"person\"," +
                "\"value_mapping\":{\"ratio\":{\"1\":1.50,\"2\":12345678901234567890.10}}}",
                VertexMapping.class);
        Object mapped = DataTypeUtil.mapValue(mapping, "ratio", "1", csv, true);
        Assert.assertEquals("1.5", DataTypeUtil.idText(mapped, csv));
        PropertyKey text = new PropertyKey.BuilderImpl("ratio", null).asText().build();
        Assert.assertEquals("1.5", DataTypeUtil.convert(mapped, text, csv));
        PropertyKey decimal = new PropertyKey.BuilderImpl("ratio", null).asDecimal().build();
        Assert.assertEquals(new BigDecimal("1.50"), DataTypeUtil.convert(mapped, decimal, csv));
        Object big = DataTypeUtil.mapValue(mapping, "ratio", "2", csv, true);
        Assert.assertEquals(new BigDecimal("12345678901234567890.10"), DataTypeUtil.convert(big, decimal, csv));
        Assert.assertEquals("1.2345678901234567E19", DataTypeUtil.convert(big, text, csv));
        // on a JSON row the replacement is handled by the JSON rule as before
        FileSource json = new FileSource();
        json.format(FileFormat.JSON);
        Object mappedJson = DataTypeUtil.mapValue(mapping, "ratio", 1, json, true);
        Assert.assertEquals(new BigDecimal("1.50"), mappedJson);
        Assert.assertEquals("1.5", DataTypeUtil.convert(mappedJson, text, json));
    }

    /** A string id built from a JSON fraction keeps the text the double path produced. */
    @Test
    public void testJsonFractionIdText() {
        FileSource json = new FileSource();
        json.format(FileFormat.JSON);
        FileSource csv = new FileSource();
        csv.format(FileFormat.CSV);
        Map<String, Object> row = JsonUtil.fromJson("{\"a\":1.50,\"b\":1e2,\"c\":1e-7,\"d\":7}", Map.class);
        Assert.assertEquals("1.5", DataTypeUtil.idText(row.get("a"), json));
        Assert.assertEquals("100.0", DataTypeUtil.idText(row.get("b"), json));
        Assert.assertEquals("1.0E-7", DataTypeUtil.idText(row.get("c"), json));
        Assert.assertEquals("7", DataTypeUtil.idText(row.get("d"), json));
        Assert.assertEquals("1.50", DataTypeUtil.idText(new BigDecimal("1.50"), csv));
    }

    /** A JSON fraction looks up the mapping by the string it always had. */
    @Test
    public void testJsonFractionMappingKey() {
        FileSource json = new FileSource();
        json.format(FileFormat.JSON);
        FileSource csv = new FileSource();
        csv.format(FileFormat.CSV);
        Map<String, Object> row = JsonUtil.fromJson(
                "{\"a\":1.00,\"b\":1e-7,\"c\":-0.0,\"d\":7,\"e\":\"1.00\"}", Map.class);
        Assert.assertEquals("1.0", DataTypeUtil.mappingKey(row.get("a"), json));
        Assert.assertEquals("1.0E-7", DataTypeUtil.mappingKey(row.get("b"), json));
        Assert.assertEquals("-0.0", DataTypeUtil.mappingKey(row.get("c"), json));
        Assert.assertEquals("7", DataTypeUtil.mappingKey(row.get("d"), json));
        Assert.assertEquals("1.00", DataTypeUtil.mappingKey(row.get("e"), json));
        // a BigDecimal from any other source keeps its own text
        Assert.assertEquals("1.00", DataTypeUtil.mappingKey(new BigDecimal("1.00"), csv));
    }
}
