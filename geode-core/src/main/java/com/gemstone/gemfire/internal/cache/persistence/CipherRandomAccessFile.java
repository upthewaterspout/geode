package com.gemstone.gemfire.internal.cache.persistence;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;

import com.gemstone.gemfire.GemFireIOException;
import com.gemstone.gemfire.internal.Assert;

public class CipherRandomAccessFile implements RandomAccessFileInterface {
  private static final int DEFAULT_BLOCK_SIZE = 512;
  private RandomAccessFileInterface raf;
  private final CipherFileChannelImpl channel;
  private boolean isClosed;
  private int plainTextBlockSize;
  private int encryptedBlockSize;
  private long position;
  private UninterruptibleFileChannel fileChannel;
  private final ByteBuffer encryptedBuffer;
  private final ByteBuffer plainTextReadBuffer;
  private long lastWritePosition;
  private final ByteBuffer plainTextWriteBuffer;
  private Cipher encryptCipher;
  private Cipher decryptCipher;

  public CipherRandomAccessFile(RandomAccessFileInterface raf, Cipher encrypt, Cipher decrypt) throws FileNotFoundException {
    this.encryptCipher = encrypt;
    this.decryptCipher = decrypt;
    int blockSize = encrypt.getBlockSize();
    this.plainTextBlockSize = blockSize == 0 ? DEFAULT_BLOCK_SIZE : blockSize;
    this.encryptedBlockSize = encrypt.getOutputSize(blockSize);
    this.raf = raf;
    this.fileChannel = raf.getChannel();
    this.channel = new CipherFileChannelImpl();
    this.encryptedBuffer = ByteBuffer.allocate(this.encryptedBlockSize);
    this.plainTextReadBuffer = ByteBuffer.allocate(this.plainTextBlockSize);
    this.plainTextWriteBuffer = plainTextReadBuffer;
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
    this.fileChannel.position(cipherBlock(this.position));
    
    long initialPosition = this.position;
    while(buf.remaining() > 0) {
      encryptedBuffer.clear();
      plainTextReadBuffer.clear();
      this.fileChannel.read(encryptedBuffer);
      try {
        decryptCipher.doFinal(encryptedBuffer, plainTextReadBuffer);
      } catch (ShortBufferException | IllegalBlockSizeException
          | BadPaddingException e) {
        throw new GemFireIOException("Error decrypting data", e);
      }

      plainTextReadBuffer.flip();
      plainTextReadBuffer.position(blockPosition(position));
      int toTransfer = Math.min(buf.remaining(), plainTextReadBuffer.remaining());
      plainTextReadBuffer.limit(plainTextReadBuffer.position() + toTransfer);
      buf.put(plainTextReadBuffer);
      position+= toTransfer;
    }
    
    return (int) (this.position - initialPosition);
  }
  
  public int write(ByteBuffer src) throws IOException {
    if(position != lastWritePosition) {
      //Read the current block if we don't already have it cached in the write buffer
      plainTextWriteBuffer.clear();
      read(plainTextWriteBuffer);
    }
    int written = 0;
    while(src.hasRemaining()) {
      plainTextWriteBuffer.put(src);
      if(!plainTextWriteBuffer.hasRemaining())
        written += saveWriteBuffer();
    }
    written += saveWriteBuffer();
    return written;
  }

  private int saveWriteBuffer() throws IOException {
    this.fileChannel.position(cipherBlock(position));
    encryptedBuffer.clear();
    try {
      encryptCipher.doFinal(plainTextWriteBuffer, encryptedBuffer);
    } catch (ShortBufferException | IllegalBlockSizeException
        | BadPaddingException e) {
      throw new GemFireIOException("Error encrypting data", e);
    }
    int writtenBytes = this.fileChannel.write(encryptedBuffer);
    position += writtenBytes;
    lastWritePosition += writtenBytes;
    Assert.assertTrue(!plainTextWriteBuffer.hasRemaining());
    return writtenBytes;
  }

  private long cipherBlock(long position) {
    return (position/ plainTextBlockSize) * encryptedBlockSize;
  }
  
  private int blockPosition(long position) {
    return (int) (position % plainTextBlockSize);
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
