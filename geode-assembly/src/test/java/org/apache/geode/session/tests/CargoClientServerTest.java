package org.apache.geode.session.tests;

import org.apache.geode.cache.Cache;
import org.apache.geode.test.dunit.Host;
import org.apache.geode.test.dunit.VM;
import org.junit.Before;

/**
 * Created by danuta on 6/7/17.
 */
public abstract class CargoClientServerTest extends CargoTestBase {
  @Before
  public void startServers() {
    Host host = Host.getHost(0);
    VM vm0 = host.getVM(0);
    vm0.invoke(() -> {
      Cache cache = getCache();
      cache.addCacheServer().start();
    });
  }
}
