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

import org.codehaus.cargo.container.configuration.LocalConfiguration;
import org.codehaus.cargo.container.deployable.WAR;
import org.codehaus.cargo.container.installer.Installer;
import org.codehaus.cargo.container.installer.ZipURLInstaller;

import java.net.MalformedURLException;
import java.net.URL;

public abstract class ContainerInstall
{
  private final String INSTALL_PATH;
  public static final String DEFAULT_INSTALL_DIR = "/tmp/cargo_containers/";

  public ContainerInstall(String installDir, String downloadURL) throws MalformedURLException
  {
    System.out.println("Installing container from URL " + downloadURL);
    // Optional step to install the container from a URL pointing to its distribution
    Installer installer = new ZipURLInstaller(
        new URL(downloadURL), "/tmp/downloads", installDir);
    installer.install();
    INSTALL_PATH = installer.getHome();
    System.out.println("Installed container into " + getInstallPath());
  }

  public String getInstallPath()
  {
    return INSTALL_PATH;
  }
  public abstract String getContainerId();
  public abstract String getContainerDescription();
  public abstract WAR getDeployableWAR();

  public abstract void setLocators(String locators) throws Exception;

  /**
   * Update the configuration of a container before it is launched,
   * if necessary.
   */
  public void modifyConfiguration(LocalConfiguration configuration) {

  }
}
