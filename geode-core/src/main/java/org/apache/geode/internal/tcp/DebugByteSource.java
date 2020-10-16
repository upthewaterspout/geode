package org.apache.geode.internal.tcp;

import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Byte Source that validated that it is never shared between threads
 */
public class DebugByteSource implements ByteBufferInputStream.ByteSource {
  private ByteBufferInputStream.ByteSource delegate;
  private final Thread owningThread;
  private final RuntimeException originalThreadStack;

  public DebugByteSource(ByteBufferInputStream.ByteSource delegate) {
    this.delegate = delegate;
    this.owningThread = Thread.currentThread();
    this.originalThreadStack =
        new RuntimeException("Original Stack Trace in Thread " + owningThread);
  }

  @Override
  public int position() {
    validateThread();
    return delegate.position();
  }

  @Override
  public int limit() {
    validateThread();
    return delegate.limit();
  }

  @Override
  public int capacity() {
    validateThread();
    return delegate.capacity();
  }

  @Override
  public int remaining() {
    validateThread();
    return delegate.remaining();
  }

  @Override
  public void position(int newPosition) {
    validateThread();
    delegate.position(newPosition);
  }

  @Override
  public void limit(int endOffset) {
    validateThread();
    delegate.limit(endOffset);
  }

  @Override
  public void get(byte[] b) {
    validateThread();
    delegate.get(b);
  }

  @Override
  public void get(byte[] b, int off, int len) {
    validateThread();
    delegate.get(b, off, len);
  }

  @Override
  public byte get() {
    validateThread();
    return delegate.get();
  }

  @Override
  public byte get(int pos) {
    validateThread();
    return delegate.get(pos);
  }

  @Override
  public short getShort() {
    validateThread();
    return delegate.getShort();
  }

  @Override
  public short getShort(int pos) {
    validateThread();
    return delegate.getShort(pos);
  }

  @Override
  public char getChar() {
    validateThread();
    return delegate.getChar();
  }

  @Override
  public char getChar(int pos) {
    validateThread();
    return delegate.getChar(pos);
  }

  @Override
  public int getInt() {
    validateThread();
    return delegate.getInt();
  }

  @Override
  public int getInt(int pos) {
    validateThread();
    return delegate.getInt(pos);
  }

  @Override
  public long getLong() {
    validateThread();
    return delegate.getLong();
  }

  @Override
  public long getLong(int pos) {
    validateThread();
    return delegate.getLong(pos);
  }

  @Override
  public float getFloat() {
    validateThread();
    return delegate.getFloat();
  }

  @Override
  public float getFloat(int pos) {
    validateThread();
    return delegate.getFloat(pos);
  }

  @Override
  public double getDouble() {
    validateThread();
    return delegate.getDouble();
  }

  @Override
  public double getDouble(int pos) {
    validateThread();
    return delegate.getDouble(pos);
  }

  @Override
  public boolean hasArray() {
    validateThread();
    return delegate.hasArray();
  }

  @Override
  public byte[] array() {
    validateThread();
    return delegate.array();
  }

  @Override
  public int arrayOffset() {
    validateThread();
    return delegate.arrayOffset();
  }

  @Override
  public ByteBufferInputStream.ByteSource duplicate() {
    validateThread();
    return delegate.duplicate();
  }

  @Override
  public ByteBufferInputStream.ByteSource slice(int length) {
    validateThread();
    return delegate.slice(length);
  }

  @Override
  public ByteBufferInputStream.ByteSource slice(int pos, int limit) {
    validateThread();
    return delegate.slice(pos, limit);
  }

  @Override
  public ByteBuffer getBackingByteBuffer() {
    validateThread();
    return delegate.getBackingByteBuffer();
  }

  @Override
  public void sendTo(ByteBuffer out) {
    validateThread();
    delegate.sendTo(out);
  }

  @Override
  public void sendTo(DataOutput out) throws IOException {
    validateThread();
    delegate.sendTo(out);
  }

  private void validateThread() {
    if (Thread.currentThread() != owningThread) {
      throw new IllegalStateException(
          "Byte buffer " + this + " is shared between two threads. Original thread " + owningThread
              + ", current thread " + Thread.currentThread(),
          originalThreadStack);
    }
  }
}
