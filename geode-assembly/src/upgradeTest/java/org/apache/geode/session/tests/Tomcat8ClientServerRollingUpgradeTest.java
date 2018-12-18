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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.apache.geode.internal.UniquePortSupplier;
import org.apache.geode.management.internal.cli.i18n.CliStrings;
import org.apache.geode.management.internal.cli.util.CommandStringBuilder;
import org.apache.geode.test.junit.categories.BackwardCompatibilityTest;
import org.apache.geode.test.junit.rules.GfshCommandRule;
import org.apache.geode.test.junit.rules.gfsh.GfshRule;
import org.apache.geode.test.junit.rules.gfsh.GfshScript;
import org.apache.geode.test.junit.runners.CategoryWithParameterizedRunnerFactory;
import org.apache.geode.test.version.VersionManager;

/**
 * This test iterates through the versions of Geode and executes session client compatibility with
 * the current version of Geode.
 */
@Category({BackwardCompatibilityTest.class})
@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(CategoryWithParameterizedRunnerFactory.class)
public class Tomcat8ClientServerRollingUpgradeTest {
  private final UniquePortSupplier portSupplier = new UniquePortSupplier();

  @Parameterized.Parameters
  public static Collection<String> data() {
    List<String> result = VersionManager.getInstance().getVersionsWithoutCurrent();
    result.removeIf(s -> Integer.parseInt(s) < 160);
    if (result.size() < 1) {
      throw new RuntimeException("No older versions of Geode were found to test against");
    }
    return result;
  }

  @Rule
  public transient GfshRule gfsh;

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Rule
  public transient TestName testName = new TestName();

  protected transient Client client;
  protected transient ContainerManager manager;

  protected File oldBuild;
  protected File oldModules;

  protected TomcatInstall tomcat8AndOldModules;
  protected TomcatInstall tomcat8AndCurrentModules;

  protected int locatorPort;
  protected String classPathTomcat8;
  private String classPathTomcat8AndOldModules;
  protected String serverDir1;
  protected String locatorDir;

  public Tomcat8ClientServerRollingUpgradeTest(String version) {
    VersionManager versionManager = VersionManager.getInstance();
    String installLocation = versionManager.getInstall(version);
    oldBuild = new File(installLocation);
    oldModules = new File(installLocation + "/tools/Modules/");
    gfsh = new GfshRule(version);
  }

  protected void startServer(String name, String classPath, int locatorPort) throws Exception {
    serverDir1 = tempFolder.newFolder("server").getPath();
    CommandStringBuilder command = new CommandStringBuilder(CliStrings.START_SERVER);
    command.addOption(CliStrings.START_SERVER__NAME, name);
    command.addOption(CliStrings.START_SERVER__SERVER_PORT, "0");
    command.addOption(CliStrings.START_SERVER__CLASSPATH, classPath);
    command.addOption(CliStrings.START_SERVER__LOCATORS, "localhost[" + locatorPort + "]");
    command.addOption(CliStrings.START_SERVER__DIR, serverDir1);
    gfsh.execute(GfshScript.of(command.toString()).expectExitCode(0));
  }

  protected void startLocator(String name, String classPath, int port) throws Exception {
    locatorDir = tempFolder.newFolder("locator").getPath();
    CommandStringBuilder locStarter = new CommandStringBuilder(CliStrings.START_LOCATOR);
    locStarter.addOption(CliStrings.START_LOCATOR__MEMBER_NAME, name);
    locStarter.addOption(CliStrings.START_LOCATOR__CLASSPATH, classPath);
    locStarter.addOption(CliStrings.START_LOCATOR__PORT, Integer.toString(port));
    locStarter.addOption(CliStrings.START_LOCATOR__DIR, locatorDir);
    gfsh.execute(GfshScript.of(locStarter.toString()).expectExitCode(0));
  }

  @Before
  public void setup() throws Exception {

    tomcat8AndOldModules =
        new TomcatInstall("Tomcat8AndOldModules", TomcatInstall.TomcatVersion.TOMCAT8,
            ContainerInstall.ConnectionType.CLIENT_SERVER,
            oldModules.getAbsolutePath(),
            oldBuild.getAbsolutePath() + "/lib",
            portSupplier::getAvailablePort);

    tomcat8AndCurrentModules =
        new TomcatInstall("Tomcat8AndCurrentModules", TomcatInstall.TomcatVersion.TOMCAT8,
            ContainerInstall.ConnectionType.CLIENT_SERVER,
            portSupplier::getAvailablePort);

    classPathTomcat8AndOldModules = tomcat8AndOldModules.getHome() + "/lib/*" + File.pathSeparator
        + tomcat8AndOldModules.getHome() + "/bin/*";

    classPathTomcat8 = tomcat8AndCurrentModules.getHome() + "/lib/*" + File.pathSeparator
        + tomcat8AndCurrentModules.getHome() + "/bin/*";

    // Get available port for the locator
    locatorPort = portSupplier.getAvailablePort();

    tomcat8AndOldModules.setDefaultLocatorPort(locatorPort);
    tomcat8AndCurrentModules.setDefaultLocatorPort(locatorPort);

    client = new Client();
    manager = new ContainerManager();
    // Due to parameterization of the test name, the URI would be malformed. Instead, it strips off
    // the [] symbols
    manager.setTestName(testName.getMethodName().replace("[", "").replace("]", ""));
  }

  protected void startClusterWithTomcat(String tomcatClassPath) throws Exception {
    startLocator("loc", tomcatClassPath, locatorPort);
    startServer("server", tomcatClassPath, locatorPort);
  }

  /**
   * Stops all containers that were previously started and cleans up their configurations
   */
  @After
  public void stop() throws Exception {
    manager.stopAllActiveContainers();
    manager.cleanUp();

    CommandStringBuilder command = new CommandStringBuilder(CliStrings.SHUTDOWN);
    command.addOption(CliStrings.INCLUDE_LOCATORS, "true");
    gfsh.execute(command.toString());
  }

  @Test
  public void doPutAndGetSessionOnAllClients() throws Exception {

    startClusterWithTomcat(classPathTomcat8AndOldModules);
    manager.addContainer(tomcat8AndOldModules);
    manager.addContainer(tomcat8AndOldModules);

    // This has to happen at the start of every test
    manager.startAllInactiveContainers();

    verifySessionReplication();
  }

  private void verifySessionReplication() throws IOException, URISyntaxException {
    String key = "value_testSessionPersists";
    String value = "Foo" + System.currentTimeMillis();

    client.setPort(Integer.parseInt(manager.getContainerPort(0)));
    Client.Response resp = client.set(key, value);
    String cookie = resp.getSessionCookie();

    for (int i = 0; i < manager.numContainers(); i++) {
      System.out.println("Checking get for container:" + i);
      client.setPort(Integer.parseInt(manager.getContainerPort(i)));
      resp = client.get(key);

      assertEquals("Sessions are not replicating properly", cookie, resp.getSessionCookie());
      assertEquals("Session data is not replicating properly", value, resp.getResponse());
    }
  }

}
