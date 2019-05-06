package org.apache.geode.test.concurrency;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.geode.annotations.Immutable;


public class FinalExampleClass {

  @Immutable
  public static final Set<String> EMPTY_SET = Collections.unmodifiableSet(new HashSet<>());
}
