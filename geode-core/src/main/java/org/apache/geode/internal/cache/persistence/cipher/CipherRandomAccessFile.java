package org.apache.geode.internal.cache.persistence.cipher;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

import javax.crypto.Cipher;

import org.apache.geode.internal.cache.persistence.RandomAccessFileInterface;
import org.apache.geode.internal.cache.persistence.UninterruptibleFileChannel;

public class CipherRandomAccessFile implements RandomAccessFileInterface {
  private final BlockIO blockIO;
  private RandomAccessFileInterface raf;
  private final CipherFileChannelImpl channel;
  private boolean isClosed;
  private long position;
  private UninterruptibleFileChannel fileChannel;
  private Cipher encryptCipher;
  private BufferManager bufferManager;

  public CipherRandomAccessFile(RandomAccessFileInterface raf, Cipher encrypt, Cipher decrypt) throws FileNotFoundException {
    this.encryptCipher = encrypt;
    this.raf = raf;
    this.fileChannel = raf.getChannel();
    this.channel = new CipherFileChannelImpl();

    this.blockIO = new BlockIO(decrypt, encrypt, this.fileChannel);
    this.bufferManager = new BufferManager(2, blockIO.getPlainTextBlockSize(), blockIO);
  }

  public UninterruptibleFileChannel getChannel() {
    return channel;
  }
  
  public synchronized void close() throws IOException {
    this.isClosed = true;
    this.raf.close();
  }

  public synchronized void setLength(long newLength) throws IOException {
    throw new UnsupportedOperationException();
  }

  public synchronized FileDescriptor getFD() throws IOException {
    return this.raf.getFD();
  }

  public synchronized long getFilePointer() throws IOException {
    return this.position;
  }

  public synchronized void seek(long readPosition) throws IOException {
    this.position = readPosition;
  }

  public synchronized void readFully(byte[] valueBytes) throws IOException {
    this.readFully(valueBytes, 0, valueBytes.length);
  }

  public synchronized void readFully(byte[] valueBytes, int i, int valueLength) throws IOException {
    ByteBuffer buf = ByteBuffer.wrap(valueBytes);
    buf.position(i);
    buf.limit(valueLength);
    int read = 0;
    while(read != valueLength) {
      read += read(buf);
    }
  }
  
  protected synchronized int read(ByteBuffer buf) throws IOException {
    long initialPosition = this.position;
    while(buf.remaining() > 0) {
      ByteBuffer plainText = bufferManager.getBuffer(block(position));
      plainText.position(blockPosition(position));
      int toTransfer = Math.min(buf.remaining(), plainText.remaining());
      int oldLimit = plainText.limit();
      plainText.limit(plainText.position() + toTransfer);
      plainText.limit(oldLimit);
      buf.put(plainText);
      position += toTransfer;
    }
    
    return (int) (this.position - initialPosition);
  }

  public long block(long position) {
    return position / blockIO.getPlainTextBlockSize();
  }
  
  public int write(ByteBuffer src) throws IOException {
    int written = 0;
    ByteBuffer plainText = null;
    while(src.hasRemaining()) {
      plainText = bufferManager.getBuffer(block(position));
      plainText.position(blockPosition(position));
      plainText.limit(plainText.capacity());
      int oldLimit = src.limit();
      src.limit(src.position() + Math.min(plainText.remaining(), src.remaining()));
      plainText.put(src);
      src.limit(oldLimit);
      if(!plainText.hasRemaining()) {
        written += save(plainText);
      }
    }
    if(plainText != null) {
      written += save(plainText);
    }
    return written;
  }

  private int save(final ByteBuffer plainText) throws IOException {
    int written = blockIO.save(block(position), plainText);
    position += written;
    return written;
  }


  private int blockPosition(long position) {
    return (int) (position % blockIO.getPlainTextBlockSize());
  }

  public synchronized long length() throws IOException {
    throw new UnsupportedOperationException();
  }

  private class CipherFileChannelImpl implements UninterruptibleFileChannel {

    @Override
    public long read(final ByteBuffer[] dsts, final int offset, final int length)
        throws IOException {
      long result = 0;
      for(int i = offset; i < length; i++) {
        result += read(dsts[i]);
      }
      return result;
    }

    @Override
    public long read(final ByteBuffer[] dsts) throws IOException {
      return read(dsts, 0, dsts.length);
    }

    @Override
    public long write(final ByteBuffer[] srcs, final int offset, final int length)
        throws IOException {
      long result = 0;
      for(int i = offset; i < length; i++) {
        result += write(srcs[i]);
      }
      return result;
    }

    @Override
    public long write(final ByteBuffer[] srcs) throws IOException {
      return write(srcs, 0, srcs.length);
    }

    @Override
    public int read(final ByteBuffer dst) throws IOException {
      return CipherRandomAccessFile.this.read(dst);
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
      return CipherRandomAccessFile.this.write(src);
    }

    @Override
    public long position() throws IOException {
      return getFilePointer();
    }

    @Override
    public SeekableByteChannel position(final long newPosition) throws IOException {
      seek(newPosition);
      return this;
    }

    @Override
    public long size() throws IOException {
      return length();
    }

    @Override
    public SeekableByteChannel truncate(final long size) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isOpen() {
      return !isClosed;
    }

    @Override
    public void close() throws IOException {
      CipherRandomAccessFile.this.close();
    }

    @Override
    public void force(final boolean b) throws IOException {
      throw new UnsupportedOperationException();
    }
    
  }


}
