package com.gemstone.gemfire.cache.jpf;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import gov.nasa.jpf.util.test.TestJPF;
import gov.nasa.jpf.vm.Verify;

public class SimpleJPFTest extends TestJPF {
  int x = 0;
  AtomicInteger atomicX = new AtomicInteger();

  @Test
  public void usingJPF_return2With2ThreadIncrements() throws InterruptedException {
    if(verifyNoPropertyViolation("+listener=gov.nasa.jpf.listener.PreciseRaceDetector", "+report.console.property_violation=error,trace")) {
      return2With2ThreadIncrements();
    };
  }

  @Test
  public void usingLoop_return2With2ThreadIncrements() throws InterruptedException {
    for(int i =0; i< 50; i++) {
      return2With2ThreadIncrements();
    }
  }

  public void return2With2ThreadIncrements() throws InterruptedException {
    x = 0;
    Runnable incrementX = () -> {x++;};
    Thread thread1 = new Thread(incrementX);
    Thread thread2 = new Thread(incrementX);
    thread1.start();
    Thread.sleep(10);
    thread2.start();
    thread1.join();
    thread2.join();
    assertEquals(2, x);
  }

  @Test
  public void return2With2ThreadAtomicIncrements() throws InterruptedException {
    if (verifyNoPropertyViolation()) {
      atomicX.set(0);
      Runnable incrementX = () -> {
        atomicX.incrementAndGet();
      };
      Thread thread1 = new Thread(incrementX);
      Thread thread2 = new Thread(incrementX);
      thread1.start();
      Thread.sleep(10);
      thread2.start();
      thread1.join();
      thread2.join();
      assertEquals(2, atomicX.get());
    }
  }

  @Test
  public void failIfJPFIsBroken() throws InterruptedException {
    if (!isJPFRun()) {
      Verify.resetCounter(0);
    }
    System.out.println(new File(".").getAbsolutePath());
    if (verifyNoPropertyViolation()) {
      Verify.incrementCounter(0);
    }
    else {
      assertTrue(Verify.getCounter(0) > 0);
    }
  }


  /**
   * Used for running test from the comamnd line outside
   * of junit runner (with jpf script)
   */
  public static void main(String[] testMethods){
    runTestsOfThisClass(testMethods);
  }
}
