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

package org.apache.hugegraph.structure.constant;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.UUID;

public enum DataType {

    OBJECT(1, "object", Serializable.class),
    BOOLEAN(2, "boolean", Boolean.class),
    BYTE(3, "byte", Byte.class),
    INT(4, "int", Integer.class),
    LONG(5, "long", Long.class),
    FLOAT(6, "float", Float.class),
    DOUBLE(7, "double", Double.class),
    TEXT(8, "text", String.class),
    BLOB(9, "blob", byte[].class),
    DATE(10, "date", Date.class),
    UUID(11, "uuid", UUID.class),
    /*
     * Arbitrary-precision decimal (java.math.BigDecimal), stored exactly by
     * the server; sent and received as a plain decimal string in JSON
     */
    DECIMAL(12, "decimal", BigDecimal.class);

    private final byte code;
    private final String name;
    private final Class<?> clazz;

    DataType(int code, String name, Class<?> clazz) {
        assert code < 256;
        this.code = (byte) code;
        this.name = name;
        this.clazz = clazz;
    }

    public byte code() {
        return this.code;
    }

    public String string() {
        return this.name;
    }

    public Class<?> clazz() {
        return this.clazz;
    }

    public boolean isNumber() {
        return this == BYTE || this == INT || this == LONG ||
               this == FLOAT || this == DOUBLE;
    }

    public boolean isDate() {
        return this == DataType.DATE;
    }

    public boolean isUUID() {
        return this == DataType.UUID;
    }

    public boolean isDecimal() {
        return this == DataType.DECIMAL;
    }

    /**
     * Convert a value to BigDecimal the same way the server does: BigDecimal
     * as is, integral numbers exactly, any other Number and a decimal string
     * through their decimal representation.
     *
     * @return the BigDecimal, or null if the value can't be a decimal
     * @throws IllegalArgumentException if the string is not a decimal number
     */
    public <V> BigDecimal valueToDecimal(V value) {
        if (!this.isDecimal()) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        } else if (value instanceof BigInteger) {
            return new BigDecimal((BigInteger) value);
        } else if (value instanceof Byte || value instanceof Short ||
                   value instanceof Integer || value instanceof Long) {
            return BigDecimal.valueOf(((Number) value).longValue());
        } else if (!(value instanceof Number) && !(value instanceof String)) {
            return null;
        }
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format(
                      "Can't read '%s' as decimal", value));
        }
    }

    public boolean isBoolean() {
        return this == BOOLEAN;
    }

    public boolean isText() {
        return this == DataType.TEXT;
    }
}
