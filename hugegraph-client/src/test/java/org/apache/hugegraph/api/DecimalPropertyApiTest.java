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

package org.apache.hugegraph.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.hugegraph.exception.ServerException;
import org.apache.hugegraph.structure.constant.DataType;
import org.apache.hugegraph.structure.constant.T;
import org.apache.hugegraph.structure.graph.BatchVertexRequest;
import org.apache.hugegraph.structure.graph.UpdateStrategy;
import org.apache.hugegraph.structure.graph.Vertex;
import org.apache.hugegraph.structure.gremlin.ResultSet;
import org.apache.hugegraph.structure.schema.PropertyKey;
import org.apache.hugegraph.structure.schema.VertexLabel;
import org.apache.hugegraph.testutil.Assert;
import org.apache.hugegraph.testutil.Utils;
import org.junit.After;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * DECIMAL property keys: exact values across create/read/batch-SUM. The
 * whole class is skipped against a server that has no DECIMAL data type yet.
 */
public class DecimalPropertyApiTest extends BaseApiTest {

    private static final String UINT256_MAX =
            "115792089237316195423570985008687907853" +
            "269984665640564039457584007913129639935";

    @BeforeClass
    public static void prepareSchema() {
        PropertyKey amount = new PropertyKey.BuilderImpl("amount", null)
                                            .asDecimal().build();
        try {
            propertyKeyAPI.create(amount);
        } catch (ServerException e) {
            Assume.assumeTrue("The server has no DECIMAL data type: " +
                              e.getMessage(), false);
        }
        propertyKeyAPI.create(new PropertyKey.BuilderImpl("name", null)
                                             .asText().build());
        vertexLabelAPI.create(new VertexLabel.BuilderImpl("account", null)
                                             .properties("name", "amount")
                                             .primaryKeys("name")
                                             .nullableKeys("amount")
                                             .build());
    }

    @Override
    @After
    public void teardown() {
        vertexAPI.list(-1).results().forEach(v -> vertexAPI.delete(v.id()));
    }

    @Test
    public void testPropertyKeyAsDecimal() {
        PropertyKey amount = propertyKeyAPI.get("amount");
        Assert.assertEquals(DataType.DECIMAL, amount.dataType());
        Assert.assertEquals(DataType.DECIMAL,
                            schema().getPropertyKey("amount").dataType());
    }

    @Test
    public void testDecimalVertexRoundTrip() {
        Vertex account = new Vertex("account");
        account.property("name", "alice");
        account.property("amount", new BigDecimal("12345678901234567890.10"));
        Object id = vertexAPI.create(account).id();

        // The value comes back as the plain decimal string, exactly
        Vertex vertex = vertexAPI.get(id);
        Assert.assertEquals("12345678901234567890.10",
                            vertex.property("amount"));
        Assert.assertEquals(new BigDecimal("12345678901234567890.10"),
                            new BigDecimal((String) vertex.property("amount")));

        // Also through the driver and Gremlin
        Vertex byDriver = graph().getVertex(id);
        Assert.assertEquals("12345678901234567890.10",
                            byDriver.property("amount"));
        ResultSet results = gremlin().gremlin(
                "g.V(vid).values('amount')").binding("vid", id).execute();
        Assert.assertEquals(ImmutableList.of("12345678901234567890.10"),
                            results.data());
    }

    @Test
    public void testSchemaManagerAndDriver() {
        // The same round trip the way an application uses the client
        schema().propertyKey("fee").asDecimal().ifNotExist().create();
        Assert.assertEquals(DataType.DECIMAL,
                            schema().getPropertyKey("fee").dataType());
        schema().vertexLabel("account").properties("fee")
                .nullableKeys("fee").append();

        Vertex account = graph().addVertex(T.LABEL, "account",
                                           "name", "grace",
                                           "amount", new BigDecimal("1.10"),
                                           "fee", new BigDecimal("0.001"));
        Assert.assertEquals("1.10", account.property("amount"));
        Assert.assertEquals("0.001", account.property("fee"));

        Vertex delta = new Vertex("account");
        delta.property("name", "grace");
        delta.property("fee", new BigDecimal("0.009"));
        Map<String, UpdateStrategy> strategies = ImmutableMap.of(
                "fee", UpdateStrategy.SUM);
        List<Vertex> updated = graph().updateVertices(
                new BatchVertexRequest.Builder()
                        .vertices(ImmutableList.of(delta))
                        .updatingStrategies(strategies)
                        .createIfNotExist(true).build());
        Assert.assertEquals(1, updated.size());
        Assert.assertEquals("0.010", updated.get(0).property("fee"));
        Assert.assertEquals("0.010",
                            graph().getVertex(account.id()).property("fee"));
    }

    @Test
    public void testDecimalValueForms() {
        // A decimal string, an integral number and uint256 max all stay exact
        Map<String, String> cases = ImmutableMap.of("bob", "0.000000000000000001",
                                                    "carol", "42",
                                                    "dave", UINT256_MAX);
        for (Map.Entry<String, String> entry : cases.entrySet()) {
            Vertex account = new Vertex("account");
            account.property("name", entry.getKey());
            account.property("amount", entry.getValue());
            Object id = vertexAPI.create(account).id();
            Assert.assertEquals(entry.getValue(),
                                vertexAPI.get(id).property("amount"));
        }

        Vertex account = new Vertex("account");
        account.property("name", "erin");
        account.property("amount", 7);
        Object id = vertexAPI.create(account).id();
        Assert.assertEquals("7", vertexAPI.get(id).property("amount"));

        Vertex bad = new Vertex("account");
        bad.property("name", "frank");
        bad.property("amount", "1,10");
        Utils.assertResponseError(400, () -> vertexAPI.create(bad));
    }

    @Test
    public void testBatchUpdateSumIsExact() {
        // Doubles would give 0.30000000000000004 and lose the 18th digit
        this.createAccounts(new BigDecimal("0.1"));
        List<Vertex> updated = this.sum(new BigDecimal("0.2"));
        for (Vertex vertex : updated) {
            Assert.assertEquals("0.3", vertex.property("amount"));
        }

        this.sum(new BigDecimal("100000000000000000000.000000000000000001"));
        this.sum(new BigDecimal("-0.3"));
        for (Vertex vertex : vertexAPI.list(-1).results()) {
            Assert.assertEquals("100000000000000000000.000000000000000001",
                                vertex.property("amount"));
        }
    }

    @Test
    public void testDecimalCanNotBeSortKeyOrRangeIndex() {
        Utils.assertResponseError(400, () -> {
            indexLabelAPI.create(
                    new org.apache.hugegraph.structure.schema.IndexLabel
                        .BuilderImpl("accountByAmount", null)
                        .onV("account").by("amount").range().build());
        });
    }

    private void createAccounts(BigDecimal amount) {
        List<Vertex> vertices = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Vertex account = new Vertex("account");
            account.property("name", "acc" + i);
            account.property("amount", amount);
            vertices.add(account);
        }
        vertexAPI.create(vertices);
    }

    private List<Vertex> sum(BigDecimal delta) {
        List<Vertex> vertices = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Vertex account = new Vertex("account");
            account.property("name", "acc" + i);
            account.property("amount", delta);
            vertices.add(account);
        }
        Map<String, UpdateStrategy> strategies = ImmutableMap.of(
                "amount", UpdateStrategy.SUM);
        BatchVertexRequest req = new BatchVertexRequest.Builder()
                .vertices(vertices).updatingStrategies(strategies)
                .createIfNotExist(true).build();
        List<Vertex> updated = vertexAPI.update(req);
        Assert.assertEquals(3, updated.size());
        return updated;
    }
}
