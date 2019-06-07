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
import static org.apache.geode.test.awaitility.GeodeAwaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

import org.apache.geode.CopyHelper;
import org.apache.geode.DataSerializable;
import org.apache.geode.DataSerializer;
import org.apache.geode.cache.Cache;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.Pool;
import org.apache.geode.cache.query.CqAttributesFactory;
import org.apache.geode.cache.query.CqEvent;
import org.apache.geode.cache.query.CqListener;
import org.apache.geode.cache.query.CqResults;
import org.apache.geode.cache.query.QueryService;
import org.apache.geode.cache.query.internal.StructImpl;
import org.apache.geode.cache.query.internal.types.StructTypeImpl;
import org.apache.geode.security.templates.UserPasswordAuthInit;
import org.apache.geode.test.dunit.VM;
import org.apache.geode.test.dunit.cache.CacheTestCase;
import org.apache.geode.test.junit.categories.SecurityTest;

@Category({SecurityTest.class})
public class PostProcessContinuousQueryDUnitTest extends CacheTestCase {

  protected static String REGION_NAME = "AuthRegion";
  final VM client1 = VM.getVM(0);
  private transient Region<Object, Object> region;

  protected Properties securityProperties() {
    Properties properties = new Properties();
    properties.setProperty(SECURITY_MANAGER, TestSecurityManager.class.getName());
    properties.setProperty(TestSecurityManager.SECURITY_JSON,
        "org/apache/geode/management/internal/security/clientServer.json");
    properties.setProperty(SECURITY_POST_PROCESSOR, TestPostProcessor.class.getName());
    return properties;
  }

  @Before
  public void createRegion() throws Exception {
    Cache cache = getCache(securityProperties());
    region = cache.createRegionFactory(RegionShortcut.REPLICATE).create(REGION_NAME);
    populateRegion(region);
    cache.addCacheServer().start();
  }

  protected void populateRegion(Region region) {
    for (int i = 0; i < 5; i++) {
      region.put(Integer.toString(i), new Value(Integer.toString(i)));
    }
  }

  @Test
  public void queryValuesShouldBeRedacted() {
    client1.invoke(() -> {
      Pool pool = createClient();
      CqListener listener = mock(CqListener.class);
      CqAttributesFactory cqAttributesFactory = new CqAttributesFactory();
      cqAttributesFactory.addCqListener(listener);
      CqResults result = pool.getQueryService()
          .newCq("cq", "select * from /AuthRegion r where r.id='1'", cqAttributesFactory.create())
          .executeWithInitialResults();
      assertThat(result).containsExactlyInAnyOrder(
          new StructImpl(new StructTypeImpl(new String[] {"key", "value"}),
              new Object[] {"1", Value.redacted(1)}));
    });

    region.put("newkey", new Value(Integer.toString(1)));

    client1.invoke(() -> {
      await().atMost(5, TimeUnit.SECONDS).untilAsserted(this::gotExpectedCQEvent);
    });
  }

  @Test
  public void fieldsShouldBeRedactedBeforeSelection() {
    client1.invoke(() -> {
      Pool pool = createClient();
      CqListener listener = mock(CqListener.class);
      CqAttributesFactory cqAttributesFactory = new CqAttributesFactory();
      cqAttributesFactory.addCqListener(listener);
      CqResults result =
          pool.getQueryService().newCq("fishing", "select * from /AuthRegion r where r.secret='1'",
              cqAttributesFactory.create()).executeWithInitialResults();
      pool.getQueryService()
          .newCq("completion", "select * from /AuthRegion r", cqAttributesFactory.create())
          .executeWithInitialResults();
      assertThat(result).isEmpty();
    });

    region.put("newkey", new Value(Integer.toString(1)));

    client1.invoke(() -> {
      // Wait until the "completion" CQ fires, to make sure events have been sent to the client
      await().untilAsserted(() -> getCqListener("completion").onEvent(any()));

      ArgumentCaptor<CqEvent> eventArgumentCaptor = ArgumentCaptor.forClass(CqEvent.class);
      verify(getCqListener("fishing"), atLeast(0)).onEvent(eventArgumentCaptor.capture());

      System.err.println("events = " + eventArgumentCaptor.getAllValues());

      // Make sure the fishing CQ does not get any events
      verify(getCqListener("fishing"), never()).onEvent(any());
    });

  }

  private Pool createClient() {
    Properties props = new Properties();
    props.setProperty(UserPasswordAuthInit.USER_NAME, "dataReader");
    props.setProperty(UserPasswordAuthInit.PASSWORD, "1234567");
    props.setProperty(SECURITY_CLIENT_AUTH_INIT, UserPasswordAuthInit.class.getName());
    ClientCacheFactory clientCacheFactory = new ClientCacheFactory(props);
    clientCacheFactory.setPoolReadTimeout(600_000);
    clientCacheFactory.setPoolSubscriptionEnabled(true);
    ClientCache cache1 = getClientCache(clientCacheFactory);

    return cache1.getDefaultPool();
  }

  private void gotExpectedCQEvent() {
    CqListener cq = getCqListener("cq");
    ArgumentCaptor<CqEvent> event = ArgumentCaptor.forClass(CqEvent.class);
    verify(cq).onEvent(event.capture());
    assertThat(event.getValue().getNewValue()).isEqualTo(Value.redacted(1));
  }

  private CqListener getCqListener(String cqName) {
    QueryService queryService = getClientCache().getDefaultPool().getQueryService();
    return queryService.getCq(cqName).getCqAttributes().getCqListener();
  }


  public static class Value implements DataSerializable {
    public String id;
    public String secret;

    public Value() {

    }

    public Value(String id) {
      this(id, id);
    }

    public Value(String id, String secret) {
      this.id = id;
      this.secret = secret;
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
    }

    @Override
    public void fromData(DataInput in) throws IOException, ClassNotFoundException {
      id = DataSerializer.readString(in);
      secret = DataSerializer.readString(in);
    }
  }

  public static class TestPostProcessor implements PostProcessor {

    @Override
    public Object processRegionValue(Object principal, String regionName, Object key,
        Object value) {
      Value result = (Value) CopyHelper.copy(value);
      result.secret = "XXX";
      return result;
    }
  }


}
