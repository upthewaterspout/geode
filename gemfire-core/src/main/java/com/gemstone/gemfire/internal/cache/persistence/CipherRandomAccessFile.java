package com.gemstone.gemfire.internal.cache.persistence;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;

import com.gemstone.gemfire.GemFireIOException;

public class CipherRandomAccessFile implements RandomAccessFileInterface {
  private static final int DEFAULT_BLOCK_SIZE = 512;
  private RandomAccessFileInterface raf;
  private final CipherFileChannelImpl channel;
  private boolean isClosed;
  private int blockSize;
  private int cipherOutputSize;
  private long position;
  private long length;
  private UninterruptibleFileChannel fileChannel;
  private final ByteBuffer encryptedBuffer;
  private final ByteBuffer plainTextBuffer;
  private long lastWritePosition;
  private final ByteBuffer writeBuffer;
  private Cipher encrypt;
  private Cipher decrypt;

  public CipherRandomAccessFile(RandomAccessFileInterface raf, Cipher encrypt, Cipher decrypt) throws FileNotFoundException {
    this.encrypt = encrypt;
    this.decrypt = decrypt;
    int blockSize = encrypt.getBlockSize();
    this.blockSize = blockSize == 0 ? DEFAULT_BLOCK_SIZE : blockSize;
    this.cipherOutputSize = encrypt.getOutputSize(blockSize);
    this.raf = raf;
    this.fileChannel = raf.getChannel();
    this.channel = new CipherFileChannelImpl();
    this.encryptedBuffer = ByteBuffer.allocate(this.cipherOutputSize);
    this.plainTextBuffer = ByteBuffer.allocate(this.blockSize);
    this.writeBuffer = plainTextBuffer;
  }

  public UninterruptibleFileChannel getChannel() {
    return channel;
  }
  
  public synchronized void close() throws IOException {
    this.isClosed = true;
    this.raf.close();
  }

  public synchronized void setLength(long newLength) throws IOException {
    this.raf.setLength(newLength);
    
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
    this.fileChannel.position(cipherBlock(this.position));
    
    long initialPosition = this.position;
    while(buf.remaining() > 0) {
      encryptedBuffer.clear();
      plainTextBuffer.clear();
      this.fileChannel.read(encryptedBuffer);
      try {
        decrypt.doFinal(encryptedBuffer, plainTextBuffer);
      } catch (ShortBufferException | IllegalBlockSizeException
          | BadPaddingException e) {
        throw new GemFireIOException("Error decrypting data", e);
      }

      plainTextBuffer.flip();
      plainTextBuffer.position(blockPosition(position));
      int toTransfer = Math.min(buf.remaining(), plainTextBuffer.remaining());
      plainTextBuffer.limit(plainTextBuffer.position() + toTransfer);
      buf.put(plainTextBuffer);
      position+= toTransfer;
    }
    
    return (int) (this.position - initialPosition);
  }
  
  public int write(ByteBuffer src) throws IOException {
    throw new UnsupportedOperationException();
  }

  private long cipherBlock(long position) {
    return (position/blockSize) * cipherOutputSize;
  }
  
  private int blockPosition(long position) {
    return (int) (position % blockSize);
  }

  public synchronized long length() throws IOException {
    return this.length;
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
      return length;
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
