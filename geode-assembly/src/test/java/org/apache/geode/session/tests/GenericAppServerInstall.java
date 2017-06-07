/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.session.tests;

import org.codehaus.cargo.container.deployable.WAR;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GenericAppServerInstall extends ContainerInstall {

  public enum Server {
    JETTY9(
        "http://central.maven.org/maven2/org/eclipse/jetty/jetty-distribution/9.4.5.v20170502/jetty-distribution-9.4.5.v20170502.zip");

    private String downloadURL;


    Server(String downloadURL) {
      this.downloadURL = downloadURL;
    }

    public String getDownloadURL() {
      return downloadURL;
    }
  }

  public enum CacheType {
    PEER_TO_PEER("peer-to-peer", "cache-peer.xml"),
    CLIENT_SERVER("client-server", "cache-client.xml");

    private final String commandLineTypeString;
    private final String XMLTypeFile;

    CacheType(String commandLineTypeString, String XMLTypeFile) {
      this.commandLineTypeString = commandLineTypeString;
      this.XMLTypeFile = XMLTypeFile;
    }

    public String getCommandLineTypeString() {
      return commandLineTypeString;
    }

    public String getXMLTypeFile() {
      return XMLTypeFile;
    }
  }

  private File warFile;
  private CacheType cacheType;
  private Server server;

  public GenericAppServerInstall(Server server) throws IOException, InterruptedException {
    this(DEFAULT_INSTALL_DIR, server, CacheType.PEER_TO_PEER);
  }

  public GenericAppServerInstall(Server server, CacheType cacheType)
      throws IOException, InterruptedException {
    this(DEFAULT_INSTALL_DIR, server, cacheType);
  }

  public GenericAppServerInstall(String installDir, Server server, CacheType cacheType)
      throws IOException, InterruptedException {
    super(installDir, server.getDownloadURL());
    this.server = server;
    this.cacheType = cacheType;

    if (cacheType == CacheType.CLIENT_SERVER) {
      modifyWarFile();
    }
  }

  private void modifyWarFile() throws IOException, InterruptedException {
    modifyWarFile("", 0);
  }

  private void modifyWarFile(String locAddress, int locPort)
      throws IOException, InterruptedException {
    String unmodifiedWar = findSessionTestingWar();

    warFile = File.createTempFile("session-testing", ".war", new File("/tmp"));
    warFile.deleteOnExit();

    String appServerModulePath = findAndExtractModule(GEODE_BUILD_HOME, "appserver");
    String modifyWarScript = appServerModulePath + "/bin/modify_war";
    new File(modifyWarScript).setExecutable(true);

    ProcessBuilder builder = new ProcessBuilder();
    builder.environment().put("GEODE", GEODE_BUILD_HOME);
    builder.inheritIO();

    List<String> command = new ArrayList<String>();
    command.add(modifyWarScript);
    command.add("-w");
    command.add(unmodifiedWar);
    command.add("-t");
    command.add(cacheType.getCommandLineTypeString());
    command.add("-o");
    command.add(warFile.getAbsolutePath());
    command.add("-p");
    command.add("gemfire.property.cache-xml-file=" + appServerModulePath + "/conf/"
        + cacheType.getXMLTypeFile());

    if (cacheType == CacheType.PEER_TO_PEER) {
      command.add("-p");
      command.add("gemfire.property.locators=" + locAddress + "[" + locPort + "]");
    }

    builder.command(command);
    System.out.println("Running command: " + String.join(" ", builder.command()));

    Process process = builder.start();

    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new IOException("Unable to run modify_war script: " + builder.command());
    }
  }

  @Override
  public String getContainerId() {
    return "jetty9x";
  }

  @Override
  public String getContainerDescription() {
    return server.name() + "_" + cacheType.name();
  }

  @Override
  public void setLocator(String address, int port) throws Exception {
    if (cacheType == CacheType.PEER_TO_PEER)
      modifyWarFile(address, port);
    else {
      HashMap<String, String> attributes = new HashMap<>();
      attributes.put("host", address);
      attributes.put("port", Integer.toString(port));

      editXMLFile(findAndExtractModule(GEODE_BUILD_HOME, "appserver") + "/conf/"
          + cacheType.getXMLTypeFile(),"locator", "pool", attributes, true);
    }
  }

  @Override
  public WAR getDeployableWAR() {
    return new WAR(warFile.getAbsolutePath());
  }
}
