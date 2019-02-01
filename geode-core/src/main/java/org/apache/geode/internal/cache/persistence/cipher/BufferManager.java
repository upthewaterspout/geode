package org.apache.geode.internal.cache.persistence.cipher;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

class BufferManager {

  private final BufferLoader bufferLoader;
  private final int bufferSize;
  private final LinkedHashMap<Long, ByteBuffer> buffers;

  public BufferManager(int size, int bufferSize, BufferLoader bufferLoader) {
    this.bufferSize = bufferSize;
    this.buffers = new LinkedHashMap<Long, ByteBuffer>(size, 0.75f, true) {

      @Override protected boolean removeEldestEntry(final Map.Entry<Long, ByteBuffer> eldest) {
        return size() > size;
      }
    };
    this.bufferLoader = bufferLoader;
  }

  public ByteBuffer getBuffer(long index)
    throws IOException
  {
    ByteBuffer result = buffers.get(index);
    if (result != null) {
      result.position(0);
      return result;
    }

    result = nextEmptyBuffer();

    bufferLoader.load(index, result);
    result.flip();
    buffers.put(index, result);
    return result;
  }

  public ByteBuffer nextEmptyBuffer() {
    Iterator<ByteBuffer> iterator = buffers.values().iterator();

    if(!iterator.hasNext()) {
      return ByteBuffer.allocate(bufferSize);
    }
    final ByteBuffer buffer = iterator.next();
    iterator.remove();
    buffer.clear();
    return buffer;
  }

  public interface BufferLoader {
    void load(long index, ByteBuffer result) throws IOException;
  }
}
