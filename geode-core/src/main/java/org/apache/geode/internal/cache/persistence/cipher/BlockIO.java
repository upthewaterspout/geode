package org.apache.geode.internal.cache.persistence.cipher;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;

import org.apache.geode.GemFireIOException;
import org.apache.geode.internal.Assert;
import org.apache.geode.internal.cache.persistence.UninterruptibleFileChannel;

class BlockIO implements BufferManager.BufferLoader {

  private final Cipher decrypt;
  private final ByteBuffer encryptedBuffer;
  private final int encryptedBlockSize;
  private static final int DEFAULT_BLOCK_SIZE = 512;
  private final int plainTextBlockSize;
  private final UninterruptibleFileChannel fileChannel;
  private final Cipher encrypt;


  public BlockIO(final Cipher decrypt,
                 final Cipher encrypt,
                 final UninterruptibleFileChannel fileChannel)
  {
    this.decrypt = decrypt;
    this.encrypt = encrypt;
    int blockSize = encrypt.getBlockSize();
    this.plainTextBlockSize = blockSize == 0 ? DEFAULT_BLOCK_SIZE : blockSize;
    this.encryptedBlockSize = encrypt.getOutputSize(blockSize);
    this.encryptedBuffer = ByteBuffer.allocate(this.encryptedBlockSize);
    this.fileChannel = fileChannel;
  }

  @Override public void load(final long index, final ByteBuffer result)
    throws IOException
  {
    fileChannel.position(index * encryptedBlockSize);
    encryptedBuffer.clear();
    result.clear();
    fileChannel.read(encryptedBuffer);
    encryptedBuffer.flip();
    try {
      decrypt.doFinal(encryptedBuffer, result);
    }
    catch (ShortBufferException | IllegalBlockSizeException | BadPaddingException e) {
      throw new GemFireIOException("Error decrypting data", e);
    }
  }

  public int save(long index, ByteBuffer plainText) throws IOException {
    plainText.flip();
    int written = plainText.remaining();
    this.fileChannel.position(index * encryptedBlockSize);
    encryptedBuffer.clear();
    try {
      encrypt.doFinal(plainText, encryptedBuffer);
    } catch (ShortBufferException | IllegalBlockSizeException
      | BadPaddingException e) {
      throw new GemFireIOException("Error encrypting data", e);
    }
    encryptedBuffer.flip();
    this.fileChannel.write(encryptedBuffer);
    Assert.assertTrue(!plainText.hasRemaining());
    return written;
  }

  public int getPlainTextBlockSize() {
    return plainTextBlockSize;
  }

  private long cipherBlock(long position) {
    return (position/ plainTextBlockSize) * encryptedBlockSize;
  }
}
