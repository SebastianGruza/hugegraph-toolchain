/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.apache.hugegraph.unit;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import org.apache.hugegraph.serializer.direct.util.BytesBuffer;
import org.apache.hugegraph.structure.constant.DataType;
import org.apache.hugegraph.structure.graph.Vertex;
import org.apache.hugegraph.structure.schema.PropertyKey;
import org.apache.hugegraph.testutil.Assert;
import org.apache.hugegraph.util.JsonUtil;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

public class DecimalDataTypeTest extends BaseUnitTest {

    @Test
    public void testDecimalDataType() {
        Assert.assertEquals(12, DataType.DECIMAL.code());
        Assert.assertEquals("decimal", DataType.DECIMAL.string());
        Assert.assertEquals(BigDecimal.class, DataType.DECIMAL.clazz());
        Assert.assertTrue(DataType.DECIMAL.isDecimal());
        // Not a Number type: no range index, no sort key, like on the server
        Assert.assertFalse(DataType.DECIMAL.isNumber());
        Assert.assertFalse(DataType.DOUBLE.isDecimal());
    }

    @Test
    public void testPropertyKeyAsDecimal() {
        PropertyKey.Builder builder = new PropertyKey.BuilderImpl("amount",
                                                                  null);
        PropertyKey amount = builder.asDecimal().build();
        Assert.assertEquals(DataType.DECIMAL, amount.dataType());
        String json = JsonUtil.toJson(amount);
        Assert.assertContains("\"data_type\":\"DECIMAL\"", json);
    }

    @Test
    public void testDecimalIsSerializedAsPlainString() {
        // Never a JSON number (would be read as a double), never E-notation
        Assert.assertEquals("\"1.10\"",
                            JsonUtil.toJson(new BigDecimal("1.10")));
        Assert.assertEquals("\"1000\"",
                            JsonUtil.toJson(new BigDecimal("1E+3")));
        Assert.assertEquals("\"0.000000000000000001\"",
                            JsonUtil.toJson(new BigDecimal("1E-18")));
        Assert.assertEquals("\"-115792089237316195423570985008687907853" +
                            "269984665640564039457584007913129639935\"",
                            JsonUtil.toJson(new BigDecimal(
                            "-115792089237316195423570985008687907853" +
                            "269984665640564039457584007913129639935")));

        Vertex vertex = new Vertex("account");
        vertex.id("account:1");
        vertex.property("balance", new BigDecimal("12345678901234567890.10"));
        String json = serialize(vertex);
        Assert.assertContains("\"balance\":\"12345678901234567890.10\"", json);

        // A value read back from the server is the plain string
        Vertex copy = deserialize(json, Vertex.class);
        Map<String, Object> props = ImmutableMap.of(
                "balance", "12345678901234567890.10");
        Assert.assertEquals(props, copy.properties());
        Assert.assertEquals(new BigDecimal("12345678901234567890.10"),
                            new BigDecimal((String) copy.property("balance")));
    }

    @Test
    public void testValueToDecimal() {
        DataType type = DataType.DECIMAL;
        Assert.assertEquals(new BigDecimal("1.10"),
                            type.valueToDecimal("1.10"));
        Assert.assertEquals(new BigDecimal("1.10"),
                            type.valueToDecimal(new BigDecimal("1.10")));
        Assert.assertEquals(BigDecimal.valueOf(42L), type.valueToDecimal(42));
        Assert.assertEquals(new BigDecimal("0.1"), type.valueToDecimal(0.1d));
        Assert.assertEquals(new BigDecimal(BigInteger.TEN),
                            type.valueToDecimal(BigInteger.TEN));
        Assert.assertNull(type.valueToDecimal(true));
        Assert.assertNull(DataType.DOUBLE.valueToDecimal("1.10"));
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            type.valueToDecimal("1,10");
        });

        org.apache.hugegraph.serializer.direct.struct.DataType direct =
                org.apache.hugegraph.serializer.direct.struct.DataType.DECIMAL;
        Assert.assertEquals(12, direct.code());
        Assert.assertTrue(direct.isDecimal());
        Assert.assertFalse(direct.isNumber());
        Assert.assertEquals(new BigDecimal("1.10"),
                            direct.valueToDecimal("1.10"));
        Assert.assertNull(org.apache.hugegraph.serializer.direct.struct
                          .DataType.DOUBLE.valueToDecimal("1.10"));
    }

    @Test
    public void testDirectSerializerDecimal() {
        // Same layout as the server: unscaled bytes then the scale
        BigDecimal value = new BigDecimal("-12345678901234567890.123456789");
        BytesBuffer buffer = BytesBuffer.allocate(64);
        buffer.writeProperty(DataType.DECIMAL, value);
        buffer.forReadWritten();
        BigInteger unscaled = new BigInteger(buffer.readBytes());
        int scale = buffer.readVInt();
        Assert.assertEquals(value, new BigDecimal(unscaled, scale));
        Assert.assertEquals(0, buffer.remaining());

        // A string reaches the direct serializer exactly as a decimal would
        buffer = BytesBuffer.allocate(16);
        buffer.writeProperty(DataType.DECIMAL, "1.10");
        buffer.forReadWritten();
        Assert.assertEquals(new BigDecimal("1.10"),
                            new BigDecimal(new BigInteger(buffer.readBytes()),
                                           buffer.readVInt()));
    }
}
