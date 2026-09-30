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
import org.apache.hugegraph.loader.util.JsonUtil;
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
    }
}
