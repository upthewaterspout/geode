package org.apache.geode.internal.cache.wan;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PointOfInterest {

  public static enum WAN {
    RESET_PEEKED, PEEKED, FILTERED, ADDED_TO_BATCHID_MAP, DISPATCHED;

  }

  private static Map<Object, Runnable> spies = new ConcurrentHashMap<>();
  private static Runnable doNothing = () -> {};

  public static void visit(Object type) {
    spies.getOrDefault(type, doNothing).run();
  }

  public static void spy(Object type, Runnable action) {
    spies.compute(type, (key, oldValue) -> oldValue == null ? action : () -> {oldValue.run(); action.run();});

  }

  public static void clear() {
    spies.clear();
  }

  private PointOfInterest() {

  }
}
