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

package org.apache.hugegraph.loader.util;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.UntypedObjectDeserializer;

/**
 * Untyped JSON values (a line of a JSON file, a mapping) with fractions as
 * BigDecimal, every digit kept for a DECIMAL column, except a negative zero:
 * BigDecimal cannot carry its sign, so {@code -0.0} stays a {@code Double}
 * and a TEXT column or an id built from it keeps "-0.0" as before.
 */
public class JsonValueDeser extends UntypedObjectDeserializer.Vanilla {

    private static final long serialVersionUID = 1L;

    @Override
    public Object deserialize(JsonParser parser, DeserializationContext context)
                              throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_FLOAT) {
            java.math.BigDecimal decimal = parser.getDecimalValue();
            if (decimal.signum() == 0 && parser.getText().startsWith("-")) {
                return -0.0d;
            }
            return decimal;
        }
        return super.deserialize(parser, context);
    }
}
