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
  public void return2_When_2ThreadsIncrement() throws InterruptedException {
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
  public void usingLoop_Return2_When_2ThreadsIncrement() throws InterruptedException {
    for(int i =0; i< 50; i++) {
      return2_When_2ThreadsIncrement();
    }
  }

  @Test
  public void usingJPF_Return2_When_2ThreadsIncrement() throws InterruptedException {
    if(verifyNoPropertyViolation()) {
      return2_When_2ThreadsIncrement();
    };
  }
}
