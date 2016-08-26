package com.gemstone.gemfire.test.dunit.standalone.classloader;

import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

import com.gemstone.gemfire.distributed.internal.DistributionConfig;
import com.gemstone.gemfire.distributed.internal.InternalLocator;
import com.gemstone.gemfire.test.dunit.standalone.DUnitLauncher;

public class ChildNode {

  /**
   * Invoked through reflection, do not change unless you also modify {@link ClassLoaderNodeManager}
   * @param namingPort
   * @param vmNum
   * @throws RemoteException
   * @throws NotBoundException
   * @throws MalformedURLException
   */
  public static void init(int namingPort, int vmNum) throws RemoteException, NotBoundException, MalformedURLException {
    System.setProperty(DUnitLauncher.RMI_PORT_PARAM, Integer.toString(namingPort));
    System.setProperty(DistributionConfig.GEMFIRE_PREFIX + "DEFAULT_MAX_OPLOG_SIZE", "10");
    System.setProperty(DistributionConfig.GEMFIRE_PREFIX + "disallowMcastDefaults", "true");
    System.setProperty(DistributionConfig.RESTRICT_MEMBERSHIP_PORT_RANGE, "true");
    if (vmNum >= 0) { // let the locator print a banner
      System.setProperty(InternalLocator.INHIBIT_DM_BANNER, "true");
    }
    DUnitLauncher.initChild(namingPort, vmNum, vmNum);

  }

}
