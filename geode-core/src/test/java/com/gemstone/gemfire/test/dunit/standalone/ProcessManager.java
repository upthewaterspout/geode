package com.gemstone.gemfire.test.dunit.standalone;

import java.io.File;
import java.io.IOException;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

public interface ProcessManager {

  static File getVMDir(int vmNum) {
    return new File(DUnitLauncher.DUNIT_DIR, "vm" + vmNum);
  }

  void launchVM(int vmNum) throws IOException;

  void killVMs();

  boolean hasLiveVMs();

  void bounce(int vmNum);

  boolean waitForVMs(long timeout) throws InterruptedException;

  RemoteDUnitVMIF getStub(int i) throws AccessException, RemoteException, NotBoundException, InterruptedException;

  void signalVMReady();
}
