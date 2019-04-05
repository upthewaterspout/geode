package org.apache.geode.internal.security;

import java.util.Iterator;
import java.util.function.Function;

class TransformingIterator<FROM, TO> implements Iterator<TO> {
  private final Iterator<? extends FROM> delegate;
  private final Function<FROM, TO> transformer;

  public TransformingIterator(
      Iterator<? extends FROM> delegate, Function<FROM, TO> transformer) {
    this.delegate = delegate;
    this.transformer = transformer;
  }

  @Override
  public boolean hasNext() {
    return delegate.hasNext();
  }

  @Override
  public TO next() {
    FROM next = delegate.next();
    if (next == null) {
      return null;
    }

    return transformer.apply(next);
  }
}
