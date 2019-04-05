package org.apache.geode.security;

import java.util.Arrays;
import java.util.function.IntFunction;

import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.server.CacheServer;
import org.apache.geode.test.dunit.VM;
import org.apache.geode.test.junit.categories.SecurityTest;
import org.apache.geode.test.junit.runners.CategoryWithParameterizedRunnerFactory;

/**
 * Test that we can post process query results when the query has to take
 * two hops - one from the client to the server and then from the server
 * to the data store.
 */
@Category({SecurityTest.class})
@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(CategoryWithParameterizedRunnerFactory.class)
public class PostProcessQueryThroughProxyDUnitTest extends PostProcessQueryDUnitTest {

  /**
   * Test a combination of these things for all index types
   * selecting the secret field vs. selecting the object
   * indexing the secret field vs. indexing a non secret field
   */
  @Parameterized.Parameters(name = "{0} - select {1} from /AuthRegion r where {2}='1'")
  public static Iterable<Object[]> parameters() {
    return Arrays.asList(new Object[][] {
        {RegionShortcut.PARTITION, "*", "id", (IntFunction<Object>) Value::redacted},
        {RegionShortcut.PARTITION, "*", "secret", (IntFunction<Object>) id -> null},
        {RegionShortcut.PARTITION, "r.secret", "id", (IntFunction<Object>) id -> "XXX"},
        {RegionShortcut.PARTITION, "r.secret", "secret", (IntFunction<Object>) id -> null}
    });
  }

  public PostProcessQueryThroughProxyDUnitTest(RegionShortcut regionType,
      String selectExpression, String indexedField,
      IntFunction<Object> expectedValueGenerator) {
    super(regionType, selectExpression, indexedField, expectedValueGenerator);
  }

  @Override
  public void createRegion() throws Exception {
    super.createRegion();

    // Stop all of the cache servers in the VM with actual data storage, so that
    // requests must come through a different server
    getCache().getCacheServers().forEach(CacheServer::stop);

    // Start up a server that only has proxy regions
    VM serverVM = VM.getVM(2);
    serverVM.invoke(() -> {
      regionType = regionType.isReplicate() ? RegionShortcut.REPLICATE_PROXY
          : RegionShortcut.PARTITION_PROXY;

      Cache cache = getCache(securityProperties());
      cache.createRegionFactory(regionType).create(REGION_NAME);
      cache.addCacheServer().start();
    });
  }
}
