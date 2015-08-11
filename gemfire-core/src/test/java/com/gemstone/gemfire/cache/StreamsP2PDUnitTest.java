package com.gemstone.gemfire.cache;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import org.junit.Test;

import com.gemstone.gemfire.cache30.CacheTestCase;

import dunit.Host;
import dunit.SerializableRunnable;
import dunit.VM;

public class StreamsP2PDUnitTest extends CacheTestCase {

  public StreamsP2PDUnitTest(String name) {
    super(name);
  }
  

  @Test
  public void testRR() {
    doTest(new SerializableRunnable() {
      public void run() {
        getCache().<Integer,Integer>createRegionFactory(RegionShortcut.REPLICATE).create("region");
      }
    });
  }
  
  @Test
  public void testPR() {
    doTest(new SerializableRunnable() {
      public void run() {
        getCache().<Integer,Integer>createRegionFactory(RegionShortcut.PARTITION).create("region");
      }
    });
  }

  private void doTest(SerializableRunnable createRegion) {
    Host host = Host.getHost(0);
    VM vm0 = host.getVM(0);
    VM vm1 = host.getVM(1);
    
    vm0.invoke(createRegion);
    vm1.invoke(createRegion);
    createRegion.run();
    
    Region<Integer, Integer> region = getCache().getRegion("region");
    
    IntStream.range(0, 10).forEach(i -> region.put(i, i));
    
    //Add all of the even integers 2 + 4 + 6 + 8;
    ArrayList<Integer> results = new ArrayList<Integer>();
    region.remoteStream()
        .filter((SerializablePredicate<Map.Entry<Integer, Integer>>) (e -> e.getKey() % 2 == 0))
        .forEach(i -> results.add(i.getValue()));
    
    //Add all of the even integers with reduce;
    int sum = region.remoteStream()
        .filter((SerializablePredicate<Map.Entry<Integer, Integer>>) (e -> e.getKey() % 2 == 0))
        .map(((Serializable & Function<Map.Entry<Integer, Integer>, Integer>) e -> e.getValue()))
        .reduce(1, (Serializable & BinaryOperator<Integer>) Integer::sum);
    
    
    assertEquals(20, results.stream().mapToInt(i -> i.intValue()).sum());
  }
  
  private static interface SerializablePredicate<T> extends Predicate<T>, Serializable {
    
  }

}
