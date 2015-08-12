/*=========================================================================
 * Copyright (c) 2002-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * more patents listed at http://www.pivotal.io/patents.
 *=========================================================================
 */

package com.gemstone.gemfire.internal.cache.extension;

import java.util.Map;

import com.gemstone.gemfire.internal.util.concurrent.CopyOnWriteHashMap;

/**
 * Simple implementation of {@link ExtensionPoint} for easy integration with
 * existing objects.
 * 
 * @author jbarrett@pivotal.io
 *
 * @since 8.1
 */
// UnitTest SimpleExtensionPointJUnitTest
public class SimpleExtensionPoint<T> implements ExtensionPoint<T> {

  protected final Map<Object, Extension<T>> extensions = new CopyOnWriteHashMap<Object, Extension<T>>();

  protected final Extensible<T> extensible;

  protected final T target;

  /**
   * Construct a new {@link SimpleExtensionPoint} around the given extensible
   * target.
   * 
   * @param extensible
   *          the {@link Extensible} object this extension point acts on.
   * 
   * @param target
   *          the <code>T</code> instance being extended. Likely the same as
   *          <code>exensible</code>.
   * @since 8.1
   */
  public SimpleExtensionPoint(final Extensible<T> extensible, final T target) {
    this.extensible = extensible;
    this.target = target;
  }

  @Override
  public Iterable<Extension<T>> getExtensions() {
    return extensions.values();
  }

  @Override
  public void addExtension(Object key, Extension<T> extension) {
    extensions.put(key, extension);
  }

  @Override
  public Extension<T> getExtension(Object key) {
    return extensions.get(key);
  }
  
  @Override
  public void removeExtension(Object key) {
    extensions.remove(key);
  }

  @Override
  public T getTarget() {
    return target;
  }

  public void fireCreate(final Extensible<T> newTarget) {
    for (final Extension<T> extension : getExtensions()) {
      extension.onCreate(extensible, newTarget);
    }
  }
}
