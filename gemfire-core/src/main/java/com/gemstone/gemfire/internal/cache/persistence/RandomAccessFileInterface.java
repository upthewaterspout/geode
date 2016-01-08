package com.gemstone.gemfire.internal.cache.persistence;

import java.io.FileDescriptor;
import java.io.IOException;

public interface RandomAccessFileInterface {

  UninterruptibleFileChannel getChannel();

  void close() throws IOException;

  void setLength(long newLength) throws IOException;

  FileDescriptor getFD() throws IOException;

  long getFilePointer() throws IOException;

  void seek(long readPosition) throws IOException;

  void readFully(byte[] valueBytes) throws IOException;

  void readFully(byte[] valueBytes, int i, int valueLength) throws IOException;

  long length() throws IOException;

}