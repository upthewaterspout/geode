package com.gemstone.gemfire.cache;

import static org.junit.Assert.assertEquals;

import java.io.Serializable;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import org.junit.After;
import org.junit.Test;

public class StreamsJUnitTest {

  @After
  public void tearDown() {
    Cache cache = CacheFactory.getAnyInstance();
    if(cache != null) {
      cache.close();
    }
  }
  
  @Test
  public void testRR() {
    CacheFactory cf = new CacheFactory();
    cf.set("mcast-port", "0");
    Cache cache = cf.create();
    
    Region<Integer, Integer> region = cache.<Integer,Integer>createRegionFactory(RegionShortcut.REPLICATE).create("region");
    
    doTest(region);
  }
  
  @Test
  public void testPR() {
    CacheFactory cf = new CacheFactory();
    cf.set("mcast-port", "0");
    Cache cache = cf.create();
    
    Region<Integer, Integer> region = cache.<Integer,Integer>createRegionFactory(RegionShortcut.PARTITION).create("region");
    
    doTest(region);
  }

  private void doTest(Region<Integer, Integer> region) {
    IntStream.range(0, 10).forEach(i -> region.put(i, i));
    
    //Add all of the even integers 2 + 4 + 6 + 8 + 10;
    int sum = region.remoteStream()
        .filter((SerializablePredicate<Map.Entry<Integer, Integer>>) (e -> e.getKey() % 2 == 0))
        .mapToInt(e -> e.getValue())
        .sum();
    
    
    assertEquals(20, sum);
  }
  
  private static interface SerializablePredicate<T> extends Predicate<T>, Serializable {
    
  }

}
