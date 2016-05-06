package com.gemstone.gemfire.cache.jpf;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.CacheFactory;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionShortcut;

import org.junit.Test;

import gov.nasa.jpf.util.test.TestJPF;
import gov.nasa.jpf.vm.Verify;

public class RegionJPFTest extends TestJPF {

  @Test
  public void noExceptionWithTwoConcurrentPuts() throws ExecutionException, InterruptedException {
    if(verifyNoPropertyViolation("+.search.heuristic.BFSHeuristic", "+report.console.property_violation=error,trace","+listener=.listener.DeadlockAnalyzer")) {
      Verify.beginAtomic();
      Region<Integer, String> region;
      ExecutorService ex;
      try {
        Cache cache = new CacheFactory().set("mcast-port", "0").set("locators", "").create();
        region = cache.<Integer, String>createRegionFactory(RegionShortcut.PARTITION).create("region");
        ex = Executors.newFixedThreadPool(2);
      }
      finally {
        Verify.endAtomic();
      }


      final Future<String> future1 = ex.submit(() -> region.put(0, "A"));
      final Future<String> future2 = ex.submit(() -> region.put(0, "B"));

      future1.get();
      future1.get();
      assertTrue(region.get(0).equals("A") || region.get(0).equals("B"));
    }

  }

}
