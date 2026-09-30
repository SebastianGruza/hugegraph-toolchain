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

package org.apache.hugegraph.serializer;

import java.io.IOException;
import java.math.BigDecimal;

import org.apache.hugegraph.structure.constant.DataType;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * Write a BigDecimal as a plain JSON number ("1.10", never "1.1E+2").
 * Jackson's default is the scientific form of BigDecimal.toString(); the
 * plain form carries every digit, so a server that reads fractions as
 * BigDecimal (apache/hugegraph#3209) stores a DECIMAL value exactly, and a
 * server that reads them as double behaves as it always did. The value stays
 * a number, so numeric keys (DOUBLE, FLOAT, LONG, INT) accept it too.
 */
public class BigDecimalSerializer extends StdSerializer<BigDecimal> {

    private static final long serialVersionUID = 1L;

    public BigDecimalSerializer() {
        super(BigDecimal.class);
    }

    @Override
    public void serialize(BigDecimal value, JsonGenerator generator,
                          SerializerProvider provider) throws IOException {
        generator.writeNumber(exactString(value));
    }

    /**
     * The plain form while the value is within the DECIMAL bounds the server
     * accepts, else the scientific form: still exact and still a JSON number,
     * but a value such as 1E+999999999 is not expanded into a billion
     * characters on the client before the server rejects it.
     */
    public static String exactString(BigDecimal value) {
        int scale = value.scale();
        if (value.precision() <= DataType.DECIMAL_MAX_PRECISION &&
            scale >= -DataType.DECIMAL_MAX_SCALE && scale <= DataType.DECIMAL_MAX_SCALE) {
            return value.toPlainString();
        }
        return value.toString();
    }
}
