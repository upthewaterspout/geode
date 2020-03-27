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
package org.apache.geode.distributed.internal.membership.gms.scheduler.test;

import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;

import org.apache.geode.distributed.internal.membership.gms.functional.CheckedThunk;
import org.apache.geode.distributed.internal.membership.gms.functional.Functions;
import org.apache.geode.distributed.internal.membership.gms.scheduler.NanoTime;
import org.apache.geode.distributed.internal.membership.gms.scheduler.Task;
import org.apache.geode.distributed.internal.membership.gms.scheduler.TaskScheduler;

public class TestScheduler implements TaskScheduler {

  final NanoTime nanoTime;
  final PriorityQueue<Task> tasks;

  public TestScheduler(final NanoTime nanoTime) {
    this.nanoTime = nanoTime;
    tasks = new PriorityQueue<>();
  }

  public void triggerActions() {
    // TODO: I'd rather go directly to the split point in a single operation instead of this loop
    final long now = nanoTime.nanoTime();
    Task head = tasks.peek();
    while (head != null && head.runnableAsOfNanos <= now) {
      tasks.remove(head);
      Functions.unchecked(head.thunk).run(); // let exceptions flow up to JUnit
      head = tasks.peek();
    }
  }

  @Override
  public void schedule(final CheckedThunk thunk, final long afterDelay, final TimeUnit delayUnit) {
    tasks.add(new Task(thunk, nanoTime.nanoTime() + delayUnit.toNanos(afterDelay)));
  }
}
