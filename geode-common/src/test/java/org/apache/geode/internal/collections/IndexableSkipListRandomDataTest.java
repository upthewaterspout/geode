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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import java.util.Iterator;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.Test;

/**
 * These tests compare the IndexableSkipList with a a TreeSet that has
 * undergone the same operations and ensures that the results match.
 */
public class IndexableSkipListRandomDataTest {
  public static final int MAX_SIZE = 500;
  public static final int MAX_VALUE = 500;
  IndexableSkipList<Long> list = new IndexableSkipList<>();
  IndexableTreeSet<Long> expected = new IndexableTreeSet<>();

  @Test
  public void addsPopulateListCorrectly() {
    insertRandomData(expected, list);
    assertEquals(expected.size(), list.size());
  }

  @Test
  public void firstFindsTheFirstElement() {
    insertRandomData(expected, list);
    assertThat(list.first()).isEqualTo(expected.first());
  }

  @Test
  public void iteratorIsInOrder() {
    insertRandomData(expected, list);
    Iterator<Long> expectedIterator = expected.iterator();
    Iterator<Long> listIterator = list.iterator();
    while (expectedIterator.hasNext()) {
      assertThat(listIterator.hasNext()).isTrue();
      assertThat(listIterator.next()).isEqualTo(expectedIterator.next());
    }
    assertThat(listIterator.hasNext()).isFalse();
  }

  @Test
  public void equalsTreeSetWithSameData() {
    insertRandomData(expected, list);
    // Equals uses contains and an iterator. Make sure that equality is true
    // in both directions
    assertThat(list.equals(expected)).isTrue();
    assertThat(expected.equals(list)).isTrue();
  }

  @Test
  public void positionFindsAllElements() {
    insertRandomData(expected, list);

    Iterator<Long> iterator = expected.iterator();
    for (int i = 0; i < expected.size(); i++) {
      Long next = iterator.next();
      assertThat(list.position(next)).isEqualTo(i);
    }
  }

  @Test
  public void randomLevelReturnsCorrectLevels() {
    assertThat(list.randomLevel(0)).isEqualTo(0);
    assertThat(list.randomLevel(0x0F)).isEqualTo(4);
    assertThat(list.randomLevel(0xF7)).isEqualTo(3);
  }



  void insertRandomData(IndexableTreeSet<Long> expected, IndexableSortedSet<Long> set) {
    final ThreadLocalRandom random = ThreadLocalRandom.current();
    int numitems = random.nextInt(MAX_SIZE);
    for (int i = 0; i < numitems; i++) {
      final long value = random.nextLong(MAX_VALUE);
      expected.add(value);
      set.add(value);
    }

  }

  private static class IndexableTreeSet<E> extends TreeSet<E> implements IndexableSortedSet<E> {

    @Override
    public int position(E e) {
      return headSet(e).size();
    }
  }


}
