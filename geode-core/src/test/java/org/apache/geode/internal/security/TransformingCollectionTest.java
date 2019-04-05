package org.apache.geode.internal.security;


import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.Test;

public class TransformingCollectionTest {

  @Test
  public void test() {
    TransformingCollection<Integer, String> collection =
        new TransformingCollection<>(Object::toString, Arrays.asList(1, 2, 3));
    assertThat(collection).containsExactly("1", "2", "3");
  }

}
