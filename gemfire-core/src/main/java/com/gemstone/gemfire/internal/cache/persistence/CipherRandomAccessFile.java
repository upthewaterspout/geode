package com.gemstone.gemfire.internal.cache.persistence;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

import javax.crypto.Cipher;

public class CipherRandomAccessFile implements RandomAccessFileInterface {
  private RandomAccessFileInterface raf;
  private final CipherFileChannelImpl channel;
  private UninterruptibleFileChannel fileChannel;
  private Cipher encrypt;
  private Cipher decrypt;

  public CipherRandomAccessFile(RandomAccessFileInterface raf, Cipher encrypt, Cipher decrypt) throws FileNotFoundException {
    this.encrypt = encrypt;
    this.decrypt = decrypt;
    this.raf = raf;
    this.fileChannel = raf.getChannel();
    this.channel = new CipherFileChannelImpl();
  }

  @Override
  public UninterruptibleFileChannel getChannel() {
    return channel;
  }

  @Override
  public void close() throws IOException {
    // TODO Auto-generated method stub
  }

  @Override
  public void setLength(long newLength) throws IOException {
    // TODO Auto-generated method stub
  }

  @Override
  public FileDescriptor getFD() throws IOException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public long getFilePointer() throws IOException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public void seek(long readPosition) throws IOException {
    // TODO Auto-generated method stub
  }

  @Override
  public void readFully(byte[] valueBytes) throws IOException {
    // TODO Auto-generated method stub
  }

  @Override
  public void readFully(byte[] valueBytes, int i, int valueLength)
      throws IOException {
    // TODO Auto-generated method stub
  }

  @Override
  public long length() throws IOException {
    // TODO Auto-generated method stub
    return 0;
  }


  private class CipherFileChannelImpl implements UninterruptibleFileChannel {

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length)
        throws IOException {
      // TODO Auto-generated method stub
      return 0;
    }

    @Override
    public long write(ByteBuffer[] srcs) throws IOException {
      // TODO Auto-generated method stub
      return 0;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
      // TODO Auto-generated method stub
      return 0;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
      // TODO Auto-generated method stub
      return 0;
    }

    @Override
    public long position() throws IOException {
      // TODO Auto-generated method stub
      return 0;
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public long size() throws IOException {
      // TODO Auto-generated method stub
      return 0;
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length)
        throws IOException {
      // TODO Auto-generated method stub
      return 0;
    }

    @Override
    public long read(ByteBuffer[] dsts) throws IOException {
      // TODO Auto-generated method stub
      return 0;
    }

    @Override
    public boolean isOpen() {
      // TODO Auto-generated method stub
      return false;
    }

    @Override
    public void close() throws IOException {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void force(boolean b) throws IOException {
      // TODO Auto-generated method stub
      
    }
    


  }



}
