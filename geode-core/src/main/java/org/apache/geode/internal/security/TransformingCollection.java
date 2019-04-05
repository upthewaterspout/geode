package org.apache.geode.internal.security;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Function;

public class TransformingCollection<FROM, TO> extends AbstractCollection<TO> {
  private final Collection<? extends FROM> delegate;
  private final Function<FROM, TO> transformer;

  public TransformingCollection(Function<FROM, TO> transformer,
      Collection<? extends FROM> delegate) {
    this.transformer = transformer;
    this.delegate = delegate;
  }

  @Override
  public Iterator<TO> iterator() {
    return new TransformingIterator<FROM, TO>(delegate.iterator(), transformer);
  }

  @Override
  public int size() {
    return delegate.size();
  }


}
