package com.gemstone.gemfire.internal.cache.persistence;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.gemstone.gemfire.internal.cache.persistence.cipher.CipherRandomAccessFile;
import com.gemstone.gemfire.test.junit.categories.UnitTest;

@Category(UnitTest.class)
public class CipherRandomAccessFileJUnitTest {
  //An arbitrary key for encryption. Maybe this should be random
  byte[] keyBytes = new byte[] {0x3, 0x67, 0x11, 0x55, 0x01, 0x03, -0x45, -0x22};
  private RandomAccessFileInterface plainRAF;
  private RandomAccessFileInterface cipherRAF;
  Random rand = new Random();
  
  @Before
  public void before() throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeySpecException, IOException, CertificateException {
    File plainFile = File.createTempFile("plainFile", "");
    plainFile.deleteOnExit();
    File cipherFile = File.createTempFile("cipherFile", "");
    cipherFile.deleteOnExit();
    plainRAF = new UninterruptibleRandomAccessFile(plainFile, "rw");
    RandomAccessFileInterface cipherBase= new UninterruptibleRandomAccessFile(cipherFile, "rw");
    CertificateFactory cf = CertificateFactory.getInstance("X.509");
    cipherRAF = new CipherRandomAccessFile(cipherBase, getEncryptCipher(), getDecryptCipher());
  }
  
  public Cipher getEncryptCipher() throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeySpecException, InvalidKeyException {
    SecretKey key = getSecretKey();  
    Cipher cipher = Cipher.getInstance("DES");
    cipher.init(Cipher.ENCRYPT_MODE, key);
    return cipher;
  }
  
  public Cipher getDecryptCipher() throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeySpecException, InvalidKeyException {
    SecretKey key = getSecretKey();  
    Cipher cipher = Cipher.getInstance("DES");
    cipher.init(Cipher.DECRYPT_MODE, key);
    return cipher;
  }

  private SecretKey getSecretKey() throws InvalidKeyException,
      NoSuchAlgorithmException, InvalidKeySpecException {
    DESKeySpec dks = new DESKeySpec(keyBytes);

    SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
    SecretKey key = keyFactory.generateSecret(dks);
    return key;
  }

  @Test
  public void testSizes()
    throws InvalidKeySpecException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException
  {
    System.out.println("encrypt block = " + getEncryptCipher().getBlockSize());
    System.out.println("decrypt block = " + getDecryptCipher().getBlockSize());
    System.out.println("encrypt output = " + getEncryptCipher().getOutputSize(getDecryptCipher().getBlockSize()));
    System.out.println("decrypt output = " + getDecryptCipher().getOutputSize(getEncryptCipher().getBlockSize()));
  }
  
  @Test
  public void testAppendData() throws IOException {
    
    appendRandomData();
    
    plainRAF.seek(0);
    cipherRAF.seek(0);

    assertTrue(IOUtils.contentEquals(Channels.newInputStream(plainRAF.getChannel()), Channels.newInputStream(cipherRAF.getChannel())));
  }

  @Test
  public void testAppend10Bytes() throws IOException {

    ByteBuffer bytes = ByteBuffer.allocate(10);
    fillRandomBytes(bytes);
    appendBytes(bytes);

    plainRAF.seek(0);
    cipherRAF.seek(0);

    assertTrue(IOUtils.contentEquals(Channels.newInputStream(plainRAF.getChannel()), Channels.newInputStream(cipherRAF.getChannel())));
  }

  private void appendRandomData() throws IOException {
    ByteBuffer bytes = ByteBuffer.allocate(1024);
    for(int i =0; i < 100; i++) {
      fillRandomBytes(bytes);
      appendBytes(bytes);
      assertEquals(plainRAF.getFilePointer(), cipherRAF.getFilePointer());
    }
  }

  private void appendBytes(final ByteBuffer bytes) throws IOException {
    bytes.mark();
    int plainWritten = plainRAF.getChannel().write(bytes);
    bytes.reset();
    int cipherWritten = cipherRAF.getChannel().write(bytes);
    assertEquals(plainWritten, cipherWritten);
  }

  @Test
  public void testRandomReads() throws IOException {
    appendRandomData();
    long length = plainRAF.length();
    byte[] plainBuffer = new byte[1024];
    byte[] cipherBuffer = new byte[1024];
    for(int i =0; i < 100; i++) {
      Arrays.fill(plainBuffer, (byte) 0);
      Arrays.fill(cipherBuffer,(byte) 0);
      int lengthToRead = rand.nextInt(plainBuffer.length);
      int location = rand.nextInt((int) length);
      plainRAF.seek(location);
      plainRAF.readFully(plainBuffer, 0, lengthToRead);
      
      cipherRAF.seek(location);
      cipherRAF.readFully(cipherBuffer, 0, lengthToRead);
      
      assertArrayEquals(plainBuffer, cipherBuffer);
      
      //Test the channel interface as well
      Arrays.fill(cipherBuffer,(byte) 0);
      cipherRAF.getChannel().position(location);
      ByteBuffer cipherByteBuffer = ByteBuffer.wrap(cipherBuffer);
      cipherByteBuffer.limit(lengthToRead);
      cipherRAF.getChannel().read(cipherByteBuffer);
      assertArrayEquals(plainBuffer, cipherBuffer);
    }
  }
  

  //Test more stuff - random writes, truncate, mixing reads and writes, etc.
  
  private void fillRandomBytes(ByteBuffer buffer) {
    buffer.clear();
    int length = rand.nextInt(buffer.capacity()) / 4;
    for(int i = 0; i < length; i++) {
      buffer.putInt(rand.nextInt());
    }
  }
  

}
