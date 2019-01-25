package org.apache.geode.test.concurrency.fates;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.security.AccessControlContext;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.github.upthewaterspout.fates.core.threading.ThreadFates;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.spi.AbstractLogger;

import org.apache.geode.test.concurrency.ParallelExecutor;
import org.apache.geode.test.concurrency.Runner;

public class FatesRunner implements Runner {
  @Override
  public List<Throwable> runTestMethod(Method child) {
    try {

      Class<?> declaringClass = child.getDeclaringClass();
      FatesConfig config = declaringClass.getAnnotation(FatesConfig.class);

      ThreadFates threadFates = new ThreadFates();
      threadFates
          .addAtomicClasses(Constructor.class)
          .addAtomicClasses(Class.class)
          .addAtomicClasses(ConcurrentHashMap.class)
          .addAtomicClasses(AbstractLogger.class)
          .addAtomicClasses(AccessControlContext.class)
          .addAtomicClasses(System.class)
          .addAtomicClasses(InetAddress.class)
          .addAtomicClasses(ThreadGroup.class)
          .addAtomicClasses(Logger.class)
          .addAtomicClasses(CompletableFuture.class)
          .setTrace(true);

      if (config != null) {
        threadFates.addAtomicClasses(config.atomicClasses());
      }
      threadFates.run(() -> {
        Object test = declaringClass.newInstance();
        ParallelExecutor executor = new FatesExecutor();
        child.invoke(test, executor);
      });
    } catch (Throwable t) {
      return Collections.singletonList(t);
    }

    return Collections.emptyList();
  }

  /**
   * A parallel executor that delegates to the optimized executor from the fates library.
   */
  private static class FatesExecutor implements ParallelExecutor {
    com.github.upthewaterspout.fates.executor.ParallelExecutor
        fates =
        new com.github.upthewaterspout.fates.executor.ParallelExecutor();

    @Override
    public <T> Future<T> inParallel(String label, Callable<T> callable) {
      CompletableFuture<T> future = new CompletableFuture<>();
      fates.inParallel(label, () -> future.complete(callable.call()));
      return future;
    }

    @Override
    public void execute() throws ExecutionException, InterruptedException {
      try {
        fates.run();
      } catch (Exception e) {
        throw new ExecutionException(e);
      }

    }
  }
}
