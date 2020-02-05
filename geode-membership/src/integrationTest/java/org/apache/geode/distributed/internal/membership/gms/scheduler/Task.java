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
package org.apache.geode.distributed.internal.membership.gms.scheduler;

import java.util.Objects;

import org.apache.geode.distributed.internal.membership.gms.functional.CheckedThunk;

/**
 * Note: this class has a natural ordering that is inconsistent with equals.
 */
public class Task implements Comparable<Task> {
  public final CheckedThunk thunk;
  public final long runnableAsOfNanos;

  public Task(final CheckedThunk thunk, final long runnableAsOfNanos) {
    this.thunk = thunk;
    this.runnableAsOfNanos = runnableAsOfNanos;
  }

  @Override
  public int compareTo(final Task o) {
    if (runnableAsOfNanos > o.runnableAsOfNanos)
      return 1;
    if (runnableAsOfNanos < o.runnableAsOfNanos)
      return -1;
    return 0;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Task task = (Task) o;
    return runnableAsOfNanos == task.runnableAsOfNanos &&
        thunk.equals(task.thunk);
  }

  @Override
  public int hashCode() {
    return Objects.hash(thunk, runnableAsOfNanos);
  }
}
