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

import static org.junit.Assert.assertEquals;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class IndexableSkipListTest {
  private IndexableSkipList<Integer> list = new IndexableSkipList<>();

  @Test
  public void addInsertsElement() {
    Assertions.assertThat(list.add(5)).isTrue();
    Assertions.assertThat(list.add(7)).isTrue();
    assertEquals(2, list.size());
  }

  @Test
  public void duplicateAddDoesNotInsert() {
    Assertions.assertThat(list.add(5)).isTrue();
    Assertions.assertThat(list.add(10)).isTrue();
    Assertions.assertThat(list.add(6)).isTrue();
    Assertions.assertThat(list.add(5)).isFalse();
    assertEquals(3, list.size());
  }

  @Test
  public void firstFindsTheFirstElement() {
    Assertions.assertThat(list.add(5)).isTrue();
    Assertions.assertThat(list.add(10)).isTrue();
    Assertions.assertThat(list.add(1)).isTrue();
    Assertions.assertThat(list.first()).isEqualTo(1);
  }

  @Test
  public void iteratorIsInOrder() {
    Assertions.assertThat(list.add(5)).isTrue();
    Assertions.assertThat(list.add(10)).isTrue();
    Assertions.assertThat(list.add(1)).isTrue();
    Assertions.assertThat(list.add(10)).isFalse();
    Iterator<Integer> iterator = list.iterator();
    Assertions.assertThat(iterator.hasNext()).isTrue();
    Assertions.assertThat(iterator.next()).isEqualTo(1);
    Assertions.assertThat(iterator.hasNext()).isTrue();
    Assertions.assertThat(iterator.next()).isEqualTo(5);
    Assertions.assertThat(iterator.hasNext()).isTrue();
    Assertions.assertThat(iterator.next()).isEqualTo(10);
    Assertions.assertThat(iterator.hasNext()).isFalse();
    Assertions.assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  public void containsIsTrueForExistingElements() {
    Assertions.assertThat(list.add(5)).isTrue();
    Assertions.assertThat(list.add(10)).isTrue();
    Assertions.assertThat(list.add(1)).isTrue();
    Assertions.assertThat(list.contains(1)).isTrue();
    Assertions.assertThat(list.contains(10)).isTrue();
    Assertions.assertThat(list.contains(5)).isTrue();
    Assertions.assertThat(list.contains(4)).isFalse();
  }

  @Test
  public void positionLocatesExistingElements() {
    System.out.println(list.prettyPrintContents());
    Assertions.assertThat(list.add(5)).isTrue();
    System.out.println(list.prettyPrintContents());
    Assertions.assertThat(list.add(10)).isTrue();
    System.out.println(list.prettyPrintContents());
    Assertions.assertThat(list.add(12)).isTrue();
    System.out.println(list.prettyPrintContents());
    Assertions.assertThat(list.add(8)).isTrue();
    System.out.println(list.prettyPrintContents());
    Assertions.assertThat(list.add(1)).isTrue();
    System.out.println(list.prettyPrintContents());
    Assertions.assertThat(list.position(1)).isEqualTo(0);
    Assertions.assertThat(list.position(5)).isEqualTo(1);
    Assertions.assertThat(list.position(8)).isEqualTo(2);
    Assertions.assertThat(list.position(10)).isEqualTo(3);
    Assertions.assertThat(list.position(12)).isEqualTo(4);
    Assertions.assertThat(list.position(7)).isEqualTo(-1);
  }


  // How can we test that that tree has a reasonable structure, and the number
  // of skips to get to a node? Or just test perf and that's it?

  // Test random level function

  // Test current level of tree after a number of inserts, and distribution of levels

  // Test memory overhead

  // Test all of the methods!

}
