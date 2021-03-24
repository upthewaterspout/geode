package org.apache.geode.internal.cache;

import java.util.function.BiFunction;

public interface RemoteEntryModification<K, V> extends BiFunction<K, V, V> {
}
