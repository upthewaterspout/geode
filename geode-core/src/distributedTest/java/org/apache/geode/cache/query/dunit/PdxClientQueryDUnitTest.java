package org.apache.geode.cache.query.dunit;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Objects;

import org.junit.Test;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.CacheException;
import org.apache.geode.cache.CacheFactory;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.client.PoolManager;
import org.apache.geode.cache.query.Query;
import org.apache.geode.cache.query.SelectResults;
import org.apache.geode.cache.server.CacheServer;
import org.apache.geode.pdx.PdxReader;
import org.apache.geode.pdx.PdxSerializable;
import org.apache.geode.pdx.PdxWriter;
import org.apache.geode.test.dunit.Host;
import org.apache.geode.test.dunit.VM;
import org.apache.geode.test.dunit.cache.internal.JUnit4CacheTestCase;

public class PdxClientQueryDUnitTest extends JUnit4CacheTestCase {
  /**
   * Tests client-server query on PdxInstance. The client receives projected value.
   */
  @Test
  public void testServerQueryOnBigDecimal() throws CacheException {

    final Host host = Host.getHost(0);
    VM vm0 = host.getVM(0);
    VM vm1 = host.getVM(1);

    // Start server1
    int serverPort = vm0.invoke(() -> {
      CacheFactory cf = new CacheFactory();
      cf.setPdxReadSerialized(true);
      Cache cache = getCache(cf);
      cache.createRegionFactory(RegionShortcut.PARTITION).create("region");
      CacheServer server = cache.addCacheServer();
      server.start();
      return server.getPort();
    });


    // Start server2
    vm1.invoke(() -> {
      ClientCacheFactory cf = new ClientCacheFactory();
      cf.addPoolServer("localhost", serverPort);
      ClientCache cache = getClientCache(cf);
      Region region = cache.createClientRegionFactory(ClientRegionShortcut.PROXY).create("region");
      region.put(1, new TestObjectWithBigDecimal(new BigDecimal(100)));
      region.put(2, new TestObjectWithBigDecimal(new BigDecimal(40)));

      // Test that numeric range queries work
      Query query = PoolManager.find(region).getQueryService()
          .newQuery("select * from /region r where r.field > $1");
      SelectResults<TestObjectWithBigDecimal> result =
          (SelectResults<TestObjectWithBigDecimal>) query.execute(new BigDecimal(50));
      assertEquals(1, result.size());
      assertEquals(new TestObjectWithBigDecimal(new BigDecimal(100)), result.iterator().next());

      // Test that numeric order by works
      Query orderByQuery = PoolManager.find(region).getQueryService()
          .newQuery("select * from /region r order by r.field");
      SelectResults<TestObjectWithBigDecimal> orderByResult =
          (SelectResults<TestObjectWithBigDecimal>) orderByQuery.execute();
      assertEquals(2, orderByResult.size());
      Iterator<TestObjectWithBigDecimal> iterator = orderByResult.iterator();
      assertEquals(new TestObjectWithBigDecimal(new BigDecimal(40)), iterator.next());
      assertEquals(new TestObjectWithBigDecimal(new BigDecimal(100)), iterator.next());
    });

  }

  public static class TestObjectWithBigDecimal implements PdxSerializable {
    private BigDecimal field;

    public TestObjectWithBigDecimal() {

    }

    public TestObjectWithBigDecimal(BigDecimal field) {
      this.field = field;
    }


    @Override
    public void toData(PdxWriter writer) {
      writer.writeObject("field", field);


    }

    @Override
    public void fromData(PdxReader reader) {
      field = (BigDecimal) reader.readObject("field");
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      TestObjectWithBigDecimal that = (TestObjectWithBigDecimal) o;
      return field.equals(that.field);
    }

    @Override
    public int hashCode() {
      return Objects.hash(field);
    }

    @Override
    public String toString() {
      return "TestObjectWithBigDecimal{" +
          "field=" + field +
          '}';
    }
  }
}
