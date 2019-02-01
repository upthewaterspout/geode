package org.apache.geode.internal.cache.persistence.cipher;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.NullCipher;

public class CipherRandomAccessFileNullCipherJUnitTest extends CipherRandomAccessFileJUnitTest {

  @Override public Cipher getEncryptCipher()
    throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeySpecException, InvalidKeyException
  {
    return new NullCipher();
  }

  @Override public Cipher getDecryptCipher()
    throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeySpecException, InvalidKeyException
  {
    return new NullCipher();
  }
}
