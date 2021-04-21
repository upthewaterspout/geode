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

package org.apache.geode.internal.collections;

import java.util.Set;

/**
 * A sorted set, which in addition to the normal sorted set operation
 * allows for finding elements of the list by position or retrieving
 * the elements at a particular position in the sorted set.
 *
 * @param <E>
 */
public interface IndexableSortedSet<E> extends Set<E> /*
                                                       * extends SortedSet<E> extends
                                                       * NavigableSet<E>
                                                       */ {
  /**
   * Return the position (rank) of the element in the sorted set, from lowest to highest element.
   *
   * The lowest element will be position 0, etc.
   *
   * @return the position of the element or -1 if the element is not in the set.
   */
  int position(E e);

  boolean add(E e);

  E first();

  int size();
}
