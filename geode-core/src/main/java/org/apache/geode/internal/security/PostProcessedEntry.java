package org.apache.geode.internal.security;

import org.apache.geode.cache.CacheStatistics;
import org.apache.geode.cache.Region;

public class PostProcessedEntry<K, V> implements Region.Entry<K, V> {

  private final Region.Entry<K, V> originalEntry;
  private final V value;

  public PostProcessedEntry(Region.Entry<K, V> originalEntry, V value) {
    this.originalEntry = originalEntry;
    this.value = value;
  }

  @Override
  public K getKey() {
    return originalEntry.getKey();
  }

  @Override
  public V getValue() {
    return value;
  }

  @Override
  public Region<K, V> getRegion() {
    return originalEntry.getRegion();
  }

  @Override
  public boolean isLocal() {
    return false;
  }

  @Override
  public CacheStatistics getStatistics() {
    return originalEntry.getStatistics();
  }

  @Override
  public Object getUserAttribute() {
    return originalEntry.getUserAttribute();
  }

  @Override
  public Object setUserAttribute(Object userAttribute) {
    return originalEntry.setUserAttribute(userAttribute);
  }

  @Override
  public boolean isDestroyed() {
    return false;
  }

  @Override
  public V setValue(V value) {
    throw new UnsupportedOperationException(
        "Post processed region entry cannot modify region value");
  }
}
