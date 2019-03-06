/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.test.concurrency.fates;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.github.upthewaterspout.fates.core.states.ExplorerSupplier;
import com.github.upthewaterspout.fates.core.states.explorers.RandomExplorer;
import com.github.upthewaterspout.fates.core.threading.ThreadFates;

import org.apache.geode.test.concurrency.ParallelExecutor;
import org.apache.geode.test.concurrency.Runner;

public class FatesRunner implements Runner, Serializable {

  @Override
  public List<Throwable> runTestMethod(Method child) {
    try {

      String methodName = child.getName();
      Class<?> declaringClass = child.getDeclaringClass();
      FatesConfig config = declaringClass.getAnnotation(FatesConfig.class);

      ThreadFates threadFates = new ThreadFates()
          .setTrace(true);

      if (config != null) {
        threadFates.addAtomicClasses(config.atomicClasses());
      }
      threadFates.setExplorer(randomExplorer());
      threadFates.run(createTest(methodName, declaringClass));
    } catch (Throwable t) {
      return Collections.singletonList(t);
    }

    return Collections.emptyList();
  }

  private static ThreadFates.MultiThreadedTest createTest(String methodName, Class<?> declaringClass) {
    return () -> {
      Object test = declaringClass.newInstance();
      ParallelExecutor executor = new FatesExecutor();
      Method method = declaringClass.getMethod(methodName, ParallelExecutor.class);
      method.invoke(test, executor);
    };
  }

  private static ExplorerSupplier randomExplorer() {
    return () -> new RandomExplorer(10000, -1883761820146619328L);
  }

  /**
   * A parallel executor that delegates to the optimized executor from the fates library.
   */
  private static class FatesExecutor implements ParallelExecutor {
    com.github.upthewaterspout.fates.executor.ParallelExecutor fates =
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
