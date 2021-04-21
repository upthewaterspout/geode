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

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A skip list implementation that supports rank operations in O(log(n)) time.
 *
 * @param <E>
 */
public class IndexableSkipList<E> extends AbstractSet<E> implements IndexableSortedSet<E> {
  // This implementation is basically straight from "A Skip List Cookbook" by William Pugh
  //
  // This is an array based skip list, like the original paper, where there is one node for
  // each element and the node has an array of pointers to the next nodes in the list.
  // On average there will be half the nodes with level i+1 as there will be with level i.
  //
  // In addition to the next pointers, each node stores the distance (number of nodes)
  // traversed by each pointer, as described in the "Linear List Operations" section
  // of "A Skip List Cookbook." We use the distances to find elements by position or
  // to find the position of an element.


  private static final SkipListNode<?> TAIL = new TailNode<>();

  private static final byte MAX_LEVEL = 32;

  private final SkipListNode<E> head = new SkipListNode<E>(null, 0);

  private final Comparator<? super E> comparator;


  @SuppressWarnings("unchecked")
  public IndexableSkipList(Comparator<? super E> comparator) {
    head.next[0] = (SkipListNode<E>) TAIL;
    this.comparator = comparator;
  }

  @SuppressWarnings("unchecked")
  public IndexableSkipList() {
    this((Comparator<E>) Comparator.naturalOrder());
  }

  @Override
  public Iterator<E> iterator() {
    return new SkipListIterator(head);
  }

  @Override
  public boolean contains(Object o) {
    @SuppressWarnings("unchecked")
    E element = (E) o;
    return position(element) != -1;
  }

  @Override
  public int position(E element) {
    SkipListNode<E> current = head;

    int position = -1;
    for (int level = head.level(); level >= 0; level--) {
      int comparison;
      while ((comparison = compare(current.next[level], element)) < 0) {
        position += current.distance[level];
        current = current.next[level];
      }

      if (comparison == 0) {
        position += current.distance[level];
        return position;
      }
    }

    return -1;
  }

  @Override
  public boolean add(E e) {
    final int newNodeLevel = randomLevel();
    SkipListNode<E> current = head;
    int position = 0;
    @SuppressWarnings("unchecked")
    SkipListNode<E>[] previousNodes = new SkipListNode[head.level() + 1];
    int[] previousPositions = new int[head.level() + 1];

    for (int level = head.level(); level >= 0; level--) {
      int comparison;
      while ((comparison = compare(current.next[level], e)) < 0) {
        // Keep track of what position we are inserting the node at
        position += current.distance[level];
        current = current.next[level];
      }

      // The element is already in the set. Return false.
      if (comparison == 0) {
        return false;
      }

      // Store the place where we need to insert the new node at this level
      previousNodes[level] = current;

      // Save the position of the previous node at this level.
      previousPositions[level] = position;
    }

    // Did not find a duplicate, and got down to level 0. Now we insert

    // Increase the position by 1 - this is now the position of the new node
    position++;

    // First, grow the head if necessary to the new level
    growHead(newNodeLevel);

    // TODO - reuse the previousNodePositions and previousNode arrays to save on allocations here?
    SkipListNode<E> newNode = new SkipListNode<>(e, newNodeLevel);

    // Insert the new node between the previous node and the next node at
    // each level
    for (int level = 0; level <= head.level(); level++) {

      // Find the previous node and position of that node
      SkipListNode<E> previousNode = level >= previousNodes.length ? head : previousNodes[level];
      int previousPosition = level >= previousPositions.length ? 0 : previousPositions[level];

      if (level <= newNodeLevel) {
        // Insert the node
        newNode.next[level] = previousNode.next[level];
        previousNode.next[level] = newNode;

        // Update the distance of the previousNode
        // This is just the position of the new node'
        // minus the position of the previous node
        int oldDistance = previousNode.distance[level];
        int newDistance = position - previousPosition;
        previousNode.distance[level] = newDistance;

        // Update the distance of the new node.
        newNode.distance[level] = oldDistance - newDistance + 1;
      } else {
        previousNode.distance[level] += 1;
      }
    }

    return true;
  }

  /**
   * Increase the level of the skip list to the new level, increasing the level of the
   * head pointer if necesary.
   */
  private void growHead(int level) {
    if (head.level() < level) {
      @SuppressWarnings("unchecked")
      SkipListNode<E>[] newNextArray = new SkipListNode[level + 1];
      int[] newDistanceArray = new int[level + 1];
      System.arraycopy(head.next, 0, newNextArray, 0, head.next.length);
      System.arraycopy(head.distance, 0, newDistanceArray, 0, head.distance.length);
      Arrays.fill(newNextArray, head.next.length, newNextArray.length, TAIL);
      // TODO - need an optimized size for this
      Arrays.fill(newDistanceArray, head.distance.length, newDistanceArray.length, size());
      head.next = newNextArray;
      head.distance = newDistanceArray;
    }

  }

  @Override
  public E first() {
    if (head.next[0] == null) {
      throw new NoSuchElementException();
    } else
      return head.next[0].element;
  }

  @Override
  public int size() {
    // TODO - Provide an optimized size
    int size = 0;
    for (SkipListNode<E> current = head; current.next[0] != TAIL; current = current.next[0]) {
      size++;
    }
    return size;
  }

  String prettyPrintContents() {
    StringBuilder results = new StringBuilder();
    for (SkipListNode<E> node = head; node != TAIL; node = node.next[0]) {
      results.append(String.format("%10s", String.valueOf(node.element)));
      for (int distance : node.distance) {
        results.append(String.format("%5d", distance));
      }
      results.append("\n");
    }

    return results.toString();
  }



  /**
   * Return if the node's element comes before the element according
   * to our comparator.
   *
   * Always returns 1 if the node is equal to {@link #TAIL}
   *
   * @return the result of the comparison between node.element and element, as specified
   *         by {@link Comparator}
   */
  private int compare(SkipListNode<E> node, E element) {
    if (node == TAIL) {
      return 1;
    }
    return comparator.compare(node.element, element);
  }

  /**
   * Returns a random level between 0 and max level. Higher levels have lower
   * probability. The probability of getting a level N is 1/2^N.
   *
   * TODO - check that math!^^
   * TODO - implement "smart" level generation, where we try to compensate for randomness in the
   * number of elements of each level? Also could consider capping the level until we need it.
   */
  private int randomLevel() {
    // TODO - is ThreadLocalRandom really the best choice?
    final ThreadLocalRandom current = ThreadLocalRandom.current();
    return randomLevel(current.nextInt());
  }

  int randomLevel(int randomInt) {
    int level = 0;
    while ((randomInt & 0x1) != 0) {
      randomInt >>>= 1;
      level++;
    }

    return level;
  }

  public static class SkipListNode<E> {

    private final E element;
    /**
     * An array of pointers to the next (greater) elements in the skip
     * list. The size of this array corresponds to the level of this node.
     */
    private SkipListNode<E>[] next;
    private int[] distance;

    @SuppressWarnings("unchecked")
    public SkipListNode(E element, int level) {
      this.element = element;
      this.next = new SkipListNode[level + 1];
      this.distance = new int[level + 1];
    }

    public int level() {
      return next.length - 1;
    }
  }

  private static class TailNode<E> extends SkipListNode<E> {
    public TailNode() {
      super(null, 0);
    }
  }

  private final class SkipListIterator implements Iterator<E> {
    private SkipListNode<E> current;

    public SkipListIterator(SkipListNode<E> head) {
      this.current = head;
    }

    @Override
    public boolean hasNext() {
      return current.next[0] != TAIL;
    }

    @Override
    public E next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      current = current.next[0];
      return current.element;
    }
  }
}
