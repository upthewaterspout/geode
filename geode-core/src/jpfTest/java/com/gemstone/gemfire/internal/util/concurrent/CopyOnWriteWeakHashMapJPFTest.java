package com.gemstone.gemfire.internal.util.concurrent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.gemstone.gemfire.internal.util.concurrent.CopyOnWriteHashMap;
import com.gemstone.gemfire.internal.util.concurrent.CopyOnWriteWeakHashMap;
import com.gemstone.gemfire.internal.util.concurrent.CustomEntryConcurrentHashMap;

import org.junit.Assert;
import org.junit.Test;

import gov.nasa.jpf.util.test.TestJPF;

public class CopyOnWriteWeakHashMapJPFTest extends TestJPF {

  @Test
  public void concurrentPutsSerializable() throws InterruptedException {
    if(verifyNoPropertyViolation()) {
      CopyOnWriteWeakHashMap map = new CopyOnWriteWeakHashMap();

      Thread thread1 = new Thread(() -> map.put(0, "A"));
      Thread thread2 = new Thread(() -> map.put(1, "B"));
      thread1.start();
      thread2.start();
      thread1.join();
      thread2.join();

      Map expected = new HashMap();
      expected.put(0, "A");
      expected.put(1, "B");
      Assert.assertEquals(expected, map);
    }
  }


}
