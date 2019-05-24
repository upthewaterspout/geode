/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.security;

import static org.apache.geode.distributed.ConfigurationProperties.SECURITY_CLIENT_AUTH_INIT;
import static org.apache.geode.distributed.ConfigurationProperties.SECURITY_MANAGER;
import static org.apache.geode.distributed.ConfigurationProperties.SECURITY_POST_PROCESSOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.apache.geode.CopyHelper;
import org.apache.geode.DataSerializable;
import org.apache.geode.DataSerializer;
import org.apache.geode.cache.Cache;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.Pool;
import org.apache.geode.cache.query.Index;
import org.apache.geode.cache.query.IndexExistsException;
import org.apache.geode.cache.query.IndexNameConflictException;
import org.apache.geode.cache.query.QueryService;
import org.apache.geode.cache.query.RegionNotFoundException;
import org.apache.geode.cache.query.SelectResults;
import org.apache.geode.cache.query.internal.index.CompactMapRangeIndex;
import org.apache.geode.cache.query.internal.index.CompactRangeIndex;
import org.apache.geode.cache.query.internal.index.HashIndex;
import org.apache.geode.cache.query.internal.index.MapRangeIndex;
import org.apache.geode.cache.query.internal.index.PartitionedIndex;
import org.apache.geode.cache.query.internal.index.PrimaryKeyIndex;
import org.apache.geode.cache.query.internal.index.RangeIndex;
import org.apache.geode.security.templates.UserPasswordAuthInit;
import org.apache.geode.test.dunit.DUnitEnv;
import org.apache.geode.test.dunit.VM;
import org.apache.geode.test.dunit.cache.CacheTestCase;
import org.apache.geode.test.junit.categories.SecurityTest;
import org.apache.geode.test.junit.runners.CategoryWithParameterizedRunnerFactory;

@Category({SecurityTest.class})
@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(CategoryWithParameterizedRunnerFactory.class)
public class PostProcessQueryDUnitTest extends CacheTestCase {

  protected Properties securityProperties() {
    Properties properties = new Properties();
    properties.setProperty(SECURITY_MANAGER, TestSecurityManager.class.getName());
    properties.setProperty(TestSecurityManager.SECURITY_JSON,
        "org/apache/geode/management/internal/security/clientServer.json");
    properties.setProperty(SECURITY_POST_PROCESSOR, TestPostProcessor.class.getName());
    return properties;
  }

  /**
   * Test a combination of these things for all index types
   * selecting the secret field vs. selecting the object
   * indexing the secret field vs. indexing a non secret field
   */
  @Parameterized.Parameters(name = "{0} - select {1} from /AuthRegion r where {2}='1'")
  public static Iterable<Object[]> parameters() {
    return Arrays.asList(new Object[][] {
        {RegionShortcut.REPLICATE, "*", "id", (IntFunction<Object>) Value::redacted},
        {RegionShortcut.REPLICATE, "*", "secret", (IntFunction<Object>) id -> null},
        {RegionShortcut.REPLICATE, "r.secret", "id", (IntFunction<Object>) id -> "XXX"},
        {RegionShortcut.REPLICATE, "r.secret", "secret", (IntFunction<Object>) id -> null},
        {RegionShortcut.PARTITION, "*", "id", (IntFunction<Object>) Value::redacted},
        {RegionShortcut.PARTITION, "*", "secret", (IntFunction<Object>) id -> null},
        {RegionShortcut.PARTITION, "r.secret", "id", (IntFunction<Object>) id -> "XXX"},
        {RegionShortcut.PARTITION, "r.secret", "secret", (IntFunction<Object>) id -> null}
    });
  }

  protected static String REGION_NAME = "AuthRegion";
  final VM client1 = VM.getVM(0);
  protected RegionShortcut regionType;
  private String selectExpression;
  private String indexedField;
  private transient IntFunction<Object> expectedValueGenerator;



  public PostProcessQueryDUnitTest(RegionShortcut regionType,
      String selectExpression, String indexedField, IntFunction<Object> expectedValueGenerator) {
    this.regionType = regionType;
    this.expectedValueGenerator = expectedValueGenerator;
    this.indexedField = indexedField;
    this.selectExpression = selectExpression;
  }

  @Before
  public void createRegion() throws Exception {
    Cache cache = getCache(securityProperties());
    Region region = cache.createRegionFactory(regionType).create(REGION_NAME);
    populateRegion(region);
    cache.addCacheServer().start();
  }

  protected void populateRegion(Region region) {
    for (int i = 0; i < 5; i++) {
      region.put(Integer.toString(i), new Value(Integer.toString(i)));
    }
  }

  @Test
  public void queryWithNoWhereClauseShouldRedact() {
    Assume.assumeTrue(indexedField.equals("id"));
    assertPostProcessed(String.format("select %s from /AuthRegion r", selectExpression),
        IntStream.range(0, 5));
  }

  @Test
  public void queryWithNoIndexShouldRedact() {
    assertPostProcessed(
        String.format("select %s from /AuthRegion r where r.%s='1'", selectExpression,
            indexedField),
        IntStream.of(1));
  }

  @Test
  public void queryWithCompactRangeIndexShouldRedact()
      throws IndexNameConflictException, IndexExistsException, RegionNotFoundException {
    QueryService queryService = getCache().getQueryService();
    Index index = queryService.createIndex("index", indexedField, "/AuthRegion");
    checkIndexType(index, CompactRangeIndex.class);
    assertPostProcessed(
        String.format("select %s from /AuthRegion r where r.%s='1'", selectExpression,
            indexedField),
        IntStream.of(1));
    assertThat(index.getStatistics().getTotalUses()).isGreaterThan(0);
    // TODO - assert that the index was used!
  }


  @Test
  public void queryWithHashIndexShouldRedact()
      throws IndexNameConflictException, IndexExistsException, RegionNotFoundException {
    QueryService queryService = getCache().getQueryService();
    Index index = queryService.createHashIndex("index", indexedField, "/AuthRegion");
    checkIndexType(index, HashIndex.class);
    assertPostProcessed(
        String.format("select %s from /AuthRegion r where r.%s='1'", selectExpression,
            indexedField),
        IntStream.of(1));
    assertThat(index.getStatistics().getTotalUses()).isGreaterThan(0);
  }

  @Test
  public void queryWithKeyIndexShouldRedact()
      throws IndexNameConflictException, IndexExistsException, RegionNotFoundException {
    Assume.assumeTrue(indexedField.equals("id"));
    QueryService queryService = getCache().getQueryService();
    Index index = queryService.createKeyIndex("index", "id", "/AuthRegion");
    checkIndexType(index, PrimaryKeyIndex.class);
    assertPostProcessed(
        String.format("select %s from /AuthRegion r where r.id='1'", selectExpression),
        IntStream.of(1));
    assertThat(index.getStatistics().getTotalUses()).isGreaterThan(0);
  }

  @Test
  public void queryWithRangeIndexShouldRedact()
      throws IndexNameConflictException, IndexExistsException, RegionNotFoundException {
    QueryService queryService = getCache().getQueryService();
    Index index =
        queryService.createIndex("index", "n." + indexedField, "/AuthRegion r, r.nested n");
    checkIndexType(index, RangeIndex.class);
    String select = selectExpression == "*" ? "r" : selectExpression;
    assertPostProcessed(String.format("select %s from /AuthRegion r, r.nested n where n.%s='1'",
        select, indexedField), IntStream.of(1));
    assertThat(index.getStatistics().getTotalUses()).isGreaterThan(0);
  }


  @Test
  public void queryWithCompactMapRangeIndexShouldRedact()
      throws IndexNameConflictException, IndexExistsException, RegionNotFoundException {
    QueryService queryService = getCache().getQueryService();
    Index index = queryService.createIndex("index", "mapField[*]", "/AuthRegion");
    checkIndexType(index, CompactMapRangeIndex.class);
    assertPostProcessed(
        String.format("select %s from /AuthRegion r where r.mapField['%s']='1'", selectExpression,
            indexedField),
        IntStream.of(1));
    assertThat(index.getStatistics().getTotalUses()).isGreaterThan(0);
  }

  @Test
  public void queryWithMapRangeIndexShouldRedact()
      throws IndexNameConflictException, IndexExistsException, RegionNotFoundException {
    QueryService queryService = getCache().getQueryService();
    Index index = queryService.createIndex("index", "n.mapField[*]", "/AuthRegion r, r.nested n");
    checkIndexType(index, MapRangeIndex.class);
    String select = selectExpression == "*" ? "r" : selectExpression;
    assertPostProcessed(
        String.format("select %s from /AuthRegion r, r.nested n where n.mapField['%s']='1'", select,
            indexedField),
        IntStream.of(1));
    assertThat(index.getStatistics().getTotalUses()).isGreaterThan(0);
  }
  /*
   *
   * @Test
   * public void queryWithMapRangeIndexShouldRedact()
   * throws IndexNameConflictException, IndexExistsException, RegionNotFoundException {
   * fail("Not yet implemented");
   * }
   *
   *
   * @Test
   * public void luceneQueryShouldRedact()
   * throws IndexNameConflictException, IndexExistsException, RegionNotFoundException {
   * // Make sure that a lucene query won't find a restricted field. Is this even possible?
   * fail("Not yet implemented");
   * }
   *
   * @Test
   * public void cqQueryShoulRedact()
   * throws IndexNameConflictException, IndexExistsException, RegionNotFoundException {
   * // Make sure that a client can't create a CQ to fish for a fields value
   * fail("Not yet implemented");
   * }
   *
   * @Test
   * public void restGfshJMXAndProtobufShouldRedact()
   * throws IndexNameConflictException, IndexExistsException, RegionNotFoundException {
   * // Make sure that REST, gfsh, and protobuf also redact some basic queries
   * fail("Not yet implemented");
   * }
   */



  private void checkIndexType(Index index, Class<?> indexClass) {
    if (regionType.isPartition()) {
      assertThat(index).isInstanceOf(PartitionedIndex.class);
      assertThat(((PartitionedIndex) index).getBucketIndexes()).hasOnlyElementsOfType(indexClass);
    } else {
      assertThat(index).isInstanceOf(indexClass);
    }
  }


  private void assertPostProcessed(String query, IntStream expectedKeys) {
    List<Object> expectedResults = expectedKeys
        .mapToObj(expectedValueGenerator)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    int port = DUnitEnv.get().getLocatorPort();
    client1.invoke(() -> {
      Properties props = new Properties();
      props.setProperty(UserPasswordAuthInit.USER_NAME, "dataReader");
      props.setProperty(UserPasswordAuthInit.PASSWORD, "1234567");
      props.setProperty(SECURITY_CLIENT_AUTH_INIT, UserPasswordAuthInit.class.getName());
      ClientCacheFactory clientCacheFactory = new ClientCacheFactory(props);
      ClientCache cache = getClientCache(clientCacheFactory);

      Pool pool = cache.getDefaultPool();
      SelectResults result = (SelectResults) pool.getQueryService().newQuery(query).execute();
      assertThat(result).containsExactlyInAnyOrder(expectedResults.toArray());
    });
  }

  public static class Value implements DataSerializable {
    public String id;
    public String secret;
    public HashSet<NestedValue> nested;
    public HashMap<String, String> mapField;

    public Value() {

    }

    public Value(String id) {
      this(id, id);
    }

    public Value(String id, String secret) {
      this(id, secret, new HashSet<>(Collections.singleton(new NestedValue(id, secret))));
    }

    public Value(String id, String secret, HashSet<NestedValue> nested) {
      this.id = id;
      this.secret = secret;
      this.nested = nested;
      this.mapField = new HashMap<>();
      mapField.put("secret", secret);
      mapField.put("id", id);
    }

    static Value redacted(int id) {
      return new Value(Integer.toString(id), "XXX");
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Value value = (Value) o;
      return id.equals(value.id) &&
          secret.equals(value.secret);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id, secret);
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + "{" +
          "id=" + id +
          ", secret='" + secret + '\'' +
          '}';
    }

    @Override
    public void toData(DataOutput out) throws IOException {
      DataSerializer.writeString(id, out);
      DataSerializer.writeString(secret, out);
      DataSerializer.writeHashSet(nested, out);
      DataSerializer.writeHashMap(mapField, out);
    }

    @Override
    public void fromData(DataInput in) throws IOException, ClassNotFoundException {
      id = DataSerializer.readString(in);
      secret = DataSerializer.readString(in);
      nested = DataSerializer.readHashSet(in);
      mapField = DataSerializer.readHashMap(in);
    }
  }

  public static class NestedValue extends Value {

    public NestedValue() {

    }

    public NestedValue(String id, String secret) {
      super(id, secret, null);
    }
  }

  public static class TestPostProcessor implements PostProcessor {

    @Override
    public Object processRegionValue(Object principal, String regionName, Object key,
        Object value) {
      Value result = (Value) CopyHelper.copy(value);
      result.secret = "XXX";
      result.mapField.put("secret", "XXX");
      result.nested.forEach(nestedValue -> nestedValue.secret = "XXX");
      result.nested.forEach(nestedValue -> nestedValue.mapField.put("secret", "XXX"));
      return result;
    }
  }
}
