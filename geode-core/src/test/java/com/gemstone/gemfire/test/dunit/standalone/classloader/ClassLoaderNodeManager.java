package com.gemstone.gemfire.test.dunit.standalone.classloader;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.gemstone.gemfire.test.dunit.standalone.ProcessManager;
import com.gemstone.gemfire.test.dunit.standalone.RemoteDUnitVMIF;

public class ClassLoaderNodeManager implements ProcessManager {
  Map<Integer, ClassLoader> nodes = new ConcurrentHashMap<>();
  private final Registry registry;
  private final int namingPort;

  public ClassLoaderNodeManager(final int namingPort, final Registry registry) {
    this.registry = registry;
    this.namingPort = namingPort;
  }

  @Override public void launchVM(final int vmNum) throws IOException {
    File workingDir = ProcessManager.getVMDir(vmNum);
    workingDir.mkdirs();

    NonDelegatingClassLoader classloader = new NonDelegatingClassLoader(Thread.currentThread().getContextClassLoader());
    try {
      final Class<?> launcherClass = Class.forName(ChildNode.class.getName(), true, classloader);
      Method initMethod = launcherClass.getMethod("init", int.class, int.class);
      initMethod.invoke(null, namingPort, vmNum);
    }
    catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
    nodes.put(vmNum, classloader);
  }

  @Override public void killVMs() {
    nodes.clear();
  }

  @Override public boolean hasLiveVMs() {
    return nodes.size() > 0;
  }

  @Override public void bounce(final int vmNum) {
    throw new UnsupportedOperationException();
  }

  @Override public boolean waitForVMs(final long timeout) throws InterruptedException {
    return true;
  }


  @Override public RemoteDUnitVMIF getStub(int i) throws AccessException, RemoteException, NotBoundException, InterruptedException {
    return (RemoteDUnitVMIF) registry.lookup("vm" + i);
  }

  @Override public void signalVMReady() {

  }
}
