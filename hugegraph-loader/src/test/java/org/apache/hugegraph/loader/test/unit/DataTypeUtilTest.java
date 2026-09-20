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

package org.apache.hugegraph.loader.test.unit;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import org.apache.hugegraph.loader.source.file.FileSource;
import org.apache.hugegraph.loader.source.file.ListFormat;
import org.apache.hugegraph.loader.util.DataTypeUtil;
import org.apache.hugegraph.structure.schema.PropertyKey;
import org.apache.hugegraph.testutil.Assert;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class DataTypeUtilTest {

    private static final FileSource SOURCE = new FileSource();

    static {
        SOURCE.listFormat(new ListFormat("", "", ","));
    }

    private static PropertyKey decimal(String name) {
        return new PropertyKey.BuilderImpl(name, null).asDecimal().build();
    }

    @Test
    public void testConvertDecimal() {
        PropertyKey amount = decimal("amount");
        Assert.assertEquals(new BigDecimal("1.10"),
                            DataTypeUtil.convert(" 1.10 ", amount, SOURCE));
        Assert.assertEquals(new BigDecimal("1.10"),
                            DataTypeUtil.convert(new BigDecimal("1.10"),
                                                 amount, SOURCE));
        Assert.assertEquals(BigDecimal.valueOf(42L),
                            DataTypeUtil.convert(42, amount, SOURCE));
        Assert.assertEquals(BigDecimal.valueOf(-7L),
                            DataTypeUtil.convert(-7L, amount, SOURCE));
        Assert.assertEquals(new BigDecimal(BigInteger.TEN),
                            DataTypeUtil.convert(BigInteger.TEN, amount,
                                                 SOURCE));
        // A double keeps its shortest round-trip representation
        Assert.assertEquals(new BigDecimal("0.1"),
                            DataTypeUtil.convert(0.1d, amount, SOURCE));
        // 78 digits (uint256 max) stay exact
        String uint256 = "115792089237316195423570985008687907853" +
                         "269984665640564039457584007913129639935";
        Assert.assertEquals(new BigDecimal(uint256),
                            DataTypeUtil.convert(uint256, amount, SOURCE));

        Assert.assertThrows(IllegalArgumentException.class, () -> {
            DataTypeUtil.convert("1,10", amount, SOURCE);
        });
        Assert.assertThrows(IllegalArgumentException.class, () -> {
            DataTypeUtil.convert(true, amount, SOURCE);
        });
    }

    @Test
    public void testConvertDecimalList() {
        PropertyKey amounts = new PropertyKey.BuilderImpl("amounts", null)
                                             .asDecimal().valueList().build();
        Object values = DataTypeUtil.convert("1.10,2", amounts, SOURCE);
        Assert.assertEquals(ImmutableList.of(new BigDecimal("1.10"),
                                             new BigDecimal("2")), values);
        // A parsed JSON list is accepted only when its elements are decimals
        List<Object> parsed = ImmutableList.of(new BigDecimal("3.30"));
        Assert.assertEquals(parsed,
                            DataTypeUtil.convert(parsed, amounts, SOURCE));
        Assert.assertThrows(IllegalStateException.class, () -> {
            DataTypeUtil.convert(ImmutableList.of(3.3d), amounts, SOURCE);
        });
    }
}
