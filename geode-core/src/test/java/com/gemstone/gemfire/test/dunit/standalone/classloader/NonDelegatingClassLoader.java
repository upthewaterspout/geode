package com.gemstone.gemfire.test.dunit.standalone.classloader;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.IllegalClassFormatException;

import org.apache.commons.io.IOUtils;

public class NonDelegatingClassLoader extends ClassLoader {

  protected NonDelegatingClassLoader(final ClassLoader parent) {
    super(parent);
  }

  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    Class<?> clazz = findLoadedClass(name);

    if(clazz != null) {
      return clazz;
    }

    if(name.startsWith("java.") || name.startsWith("sun.") || name.startsWith("com.sun.") || name.startsWith("javax.") || name.startsWith("org.xml.") || name.startsWith("org.w3c.")) {
      return super.loadClass(name, resolve);
    }
    return findClass(name);
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    String className = name.replace(".", "/") + ".class";
    //This doesn't work for JDK classes because the JDK won't let us find
    //the JDK class and even if we work around that, the JDK won't let us
    //define the JDK class in our classloader.
    //Technically, we could probably transform all of the code to point to tranformed version of
    //JDK classes, but that would make debugging painful.
    try {
      InputStream is = getParent().getResourceAsStream(className);
      if (is == null) {
        return getParent().loadClass(name);
//        throw new IllegalStateException("Could not find class with name " + name);
      }
      byte[] classBytes;
      classBytes = IOUtils.toByteArray(is);
      is.close();

      return defineClass(name, classBytes, 0, classBytes.length, this.getClass().getProtectionDomain());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

}
