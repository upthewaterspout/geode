package org.apache.geode.session.tests;

import org.codehaus.cargo.container.ContainerType;
import org.codehaus.cargo.container.InstalledLocalContainer;
import org.codehaus.cargo.container.State;
import org.codehaus.cargo.container.configuration.ConfigurationType;
import org.codehaus.cargo.container.configuration.LocalConfiguration;
import org.codehaus.cargo.container.deployable.WAR;
import org.codehaus.cargo.container.property.GeneralPropertySet;
import org.codehaus.cargo.container.property.LoggingLevel;
import org.codehaus.cargo.container.property.ServletPropertySet;
import org.codehaus.cargo.generic.DefaultContainerFactory;
import org.codehaus.cargo.generic.configuration.DefaultConfigurationFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class ContainerManager
{
  private static final String PLAIN_LOG_FILE = "/tmp/logs/container";

  private ArrayList<InstalledLocalContainer> containers;
  private ArrayList<ContainerInstall> installs;

  public ContainerManager()
  {
    containers = new ArrayList<>();
    installs = new ArrayList<>();
  }

  public InstalledLocalContainer addContainer(ContainerInstall install, int index) throws IOException
  {
    // Create the Cargo Container instance wrapping our physical container
    LocalConfiguration configuration = (LocalConfiguration) new DefaultConfigurationFactory().createConfiguration(
        install.getContainerId(), ContainerType.INSTALLED, ConfigurationType.STANDALONE, "/tmp/cargo_configs/config" + index);
    configuration.setProperty(GeneralPropertySet.LOGGING, LoggingLevel.HIGH.getLevel());

    configuration.setProperty(ServletPropertySet.PORT, Integer.toString(8080));
    configuration.setProperty(GeneralPropertySet.RMI_PORT, Integer.toString(7000));
    configuration.setProperty(GeneralPropertySet.PORT_OFFSET, Integer.toString(index));
    configuration.applyPortOffset();

    install.modifyConfiguration(configuration);

//    configuration.setProperty(GeneralPropertySet.JVMARGS, "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=" + (7700 + index));
//    configuration.setProperty(GeneralPropertySet.JVMARGS, "-Dtomcat.util.scan.DefaultJarScanner.jarsToSkip=* -Dorg.apache.catalina.startup.ContextConfig.jarsToSkip=* -Dorg.apache.catalina.startup.TldConfig.jarsToSkip=*");

    // Statically deploy WAR file for servlet
    WAR war = install.getDeployableWAR();
    war.setContext("");
    configuration.addDeployable(war);
    System.out.println("Deployed WAR file found at " + war.getFile());

    // Create the container, set it's home dir to where it was installed, and set the its output log
    InstalledLocalContainer container =
        (InstalledLocalContainer) (new DefaultContainerFactory()).createContainer(
            install.getContainerId(), ContainerType.INSTALLED, configuration);

    container.setHome(install.getInstallPath());
    container.setOutput(PLAIN_LOG_FILE + containers.size() + ".log");
    System.out.println("Sending log file output to " + PLAIN_LOG_FILE + containers.size() + ".log");

    containers.add(index, container);
    installs.add(index, install);

    System.out.println("Setup container " + getContainerDescription(index) + "\n-----------------------------------------");
    return container;
  }

  public InstalledLocalContainer addContainer(ContainerInstall install) throws IOException
  {
    return addContainer(install, containers.size());
  }

  public void addContainers(int numContainers, ContainerInstall install) throws IOException
  {
    for (int i = 0; i < numContainers; i++)
      addContainer(install);
  }

  public InstalledLocalContainer editContainer(ContainerInstall install, int index) throws IOException
  {
    stopContainer(index);
    return addContainer(install, index);
  }

  public String getContainerPort(int index)
  {
    LocalConfiguration config = getContainer(index).getConfiguration();
    config.applyPortOffset();
    return config.getPropertyValue(ServletPropertySet.PORT);
  }

  public int numContainers()
  {
    return containers.size();
  }

  public ArrayList<Integer> getContainerIndexesWithState(String state)
  {
    ArrayList<Integer> indexes = new ArrayList<>();
    for (int i = 0; i < numContainers(); i++)
    {
      if (state.equals(State.STARTED.toString()) || state.equals(State.STOPPED.toString()) || state.equals(State.STARTED.toString()) || state.equals(State.STOPPING.toString()) || state.equals(State.UNKNOWN.toString())) {
        if (getContainer(i).getState().toString().equals(state))
          indexes.add(i);
      }
      else
        throw new IllegalArgumentException("State must be one of the 5 specified cargo state strings (stopped, started, starting, stopping, or unknown). Given: " + state);
    }
    return indexes;
  }

  public ArrayList<InstalledLocalContainer> getContainersWithState(String state)
  {
    ArrayList<InstalledLocalContainer> statedContainers = new ArrayList<>();
    for (int index : getContainerIndexesWithState(state))
      statedContainers.add(getContainer(index));
    return statedContainers;
  }

  public ArrayList<Integer> getInactiveContainerIndexes()
  {
    ArrayList<Integer> indexes = getContainerIndexesWithState(State.STOPPED.toString());
    indexes.addAll(getContainerIndexesWithState(State.UNKNOWN.toString()));
    return indexes;
  }

  public ArrayList<InstalledLocalContainer> getInactiveContainers()
  {
    ArrayList<InstalledLocalContainer> inactiveContainers = getContainersWithState(State.STOPPED.toString());
    inactiveContainers.addAll(getContainersWithState(State.UNKNOWN.toString()));
    return inactiveContainers;
  }

  public ArrayList<Integer> getActiveContainerIndexes() { return getContainerIndexesWithState(State.STARTED.toString()); }
  public ArrayList<InstalledLocalContainer> getActiveContainers() { return getContainersWithState(State.STARTED.toString()); }

  public InstalledLocalContainer getContainer(int index)
  {
    return containers.get(index);
  }
  public ContainerInstall getContainerInstall(int index) { return installs.get(index); }
  public String getContainerDescription(int index) { return getContainerInstall(index).getContainerDescription() + ":" + getContainerPort(index); }
  public void startContainer(int index)
  {
    InstalledLocalContainer container = getContainer(index);
    if (!container.getState().isStarted()) {
      container.start();
      System.out.println("Started container" + index + " " + getContainerDescription(index));
    }
    else
      throw new IllegalArgumentException("Cannot start container" + index + " " + getContainerDescription(index) + " it has currently " + container.getState());
  }
  public void stopContainer(int index)
  {
    InstalledLocalContainer container = getContainer(index);
    if (container.getState().isStarted()) {
      container.stop();
      System.out.println("Stopped container" + index + " " + getContainerDescription(index));
    }
    else
      throw new IllegalArgumentException("Cannot stop container" + index + " " + getContainerDescription(index) + " it is currently " + container.getState());
  }
  public void startContainers(int[] indexes)
  {
    for (int index : indexes)
      startContainer(index);
  }
  public void startContainers(ArrayList<Integer> indexes)
  {
    for (int index : indexes)
      startContainer(index);
  }
  public void stopContainers(int[] indexes)
  {
    for (int index : indexes)
      stopContainer(index);
  }
  public void stopContainers(ArrayList<Integer> indexes)
  {
    for (int index : indexes)
      stopContainer(index);
  }
  public void startAllInactiveContainers()
  {
    startContainers(getInactiveContainerIndexes());
  }
  public void stopAllActiveContainers()
  {
    stopContainers(getActiveContainerIndexes());
  }

  public void removeContainer(int index)
  {
    stopContainer(index);
    containers.remove(index);
    installs.remove(index);
  }
}
