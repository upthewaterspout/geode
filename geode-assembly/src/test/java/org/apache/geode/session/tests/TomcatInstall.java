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

import org.apache.catalina.startup.Tomcat;
import org.apache.commons.io.FilenameUtils;
import org.apache.geode.management.internal.configuration.utils.ZipUtils;
import org.codehaus.cargo.container.configuration.FileConfig;
import org.codehaus.cargo.container.configuration.LocalConfiguration;
import org.codehaus.cargo.container.deployable.WAR;
import org.codehaus.cargo.container.installer.Installer;
import org.codehaus.cargo.container.installer.ZipURLInstaller;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.awt.Container;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class TomcatInstall extends ContainerInstall {

  public enum TomcatVersion {
    TOMCAT6(6,
        "http://archive.apache.org/dist/tomcat/tomcat-6/v6.0.37/bin/apache-tomcat-6.0.37.zip"),
    TOMCAT7(7,
        "http://archive.apache.org/dist/tomcat/tomcat-7/v7.0.73/bin/apache-tomcat-7.0.73.zip"),
    TOMCAT8(8,
        "http://archive.apache.org/dist/tomcat/tomcat-8/v8.5.15/bin/apache-tomcat-8.5.15.zip"),
    TOMCAT9(9,
        "http://archive.apache.org/dist/tomcat/tomcat-9/v9.0.0.M21/bin/apache-tomcat-9.0.0.M21.zip");

    private final int version;
    private final String downloadURL;

    TomcatVersion(int version, String downloadURL) {
      this.version = version;
      this.downloadURL = downloadURL;
    }

    public String downloadURL() {
      return downloadURL;
    }

    public int toInteger() {
      return version;
    }

    public String jarSkipPropertyName() {
      switch (this) {
        case TOMCAT6:
          return null;
        case TOMCAT7:
          return "tomcat.util.scan.DefaultJarScanner.jarsToSkip";
        case TOMCAT8:
        case TOMCAT9:
          return "tomcat.util.scan.StandardJarScanFilter.jarsToSkip";
        default:
          throw new IllegalArgumentException("Illegal tomcat version option");
      }
    }

    public HashMap<String, String> getRequiredXMLAttributes() {
      HashMap<String, String> attributes = new HashMap<>();

      int sessionManagerNum;
      switch (this) {
        case TOMCAT9:
          sessionManagerNum = 8;
          break;
        default:
          sessionManagerNum = this.toInteger();
      }
      attributes.put("className", "org.apache.geode.modules.session.catalina.Tomcat"
          + sessionManagerNum + "DeltaSessionManager");
      return attributes;
    }
  }

  public enum TomcatConfig {
    PEER_TO_PEER("org.apache.geode.modules.session.catalina.PeerToPeerCacheLifecycleListener", "cache-peer.xml"),
    CLIENT_SERVER("org.apache.geode.modules.session.catalina.ClientServerCacheLifecycleListener", "cache-client.xml");

    private final String XMLClassName;
    private final String XMLFile;

    TomcatConfig(String XMLClassName, String XMLFile) {
      this.XMLClassName = XMLClassName;
      this.XMLFile = XMLFile;
    }

    public String getXMLClassName() {
      return XMLClassName;
    }

    public String getXMLFile() {
      return XMLFile;
    }

    public HashMap<String, String> getRequiredXMLAttributes() throws IOException {
      HashMap<String, String> attributes = new HashMap<>();
      attributes.put("className", XMLClassName);
      return attributes;
    }
  }

  private static final String[] tomcatRequiredJars =
      {"antlr", "commons-lang", "fastutil", "geode-core", "geode-modules", "geode-modules-tomcat7",
          "geode-modules-tomcat8", "javax.transaction-api", "jgroups", "log4j-api", "log4j-core",
          "log4j-jul", "shiro-core", "slf4j-api", "slf4j-jdk14"};

  private TomcatConfig config;
  private final TomcatVersion version;
  private final String tomcatModulePath;

  public TomcatInstall(TomcatVersion version) throws Exception {
    this(version, TomcatConfig.PEER_TO_PEER, DEFAULT_INSTALL_DIR);
  }

  public TomcatInstall(TomcatVersion version, String installDir) throws Exception {
    this(version, TomcatConfig.PEER_TO_PEER, installDir);
  }

  public TomcatInstall(TomcatVersion version, TomcatConfig config) throws Exception {
    this(version, config, DEFAULT_INSTALL_DIR);
  }

  private TomcatInstall(TomcatVersion version, TomcatConfig config, String installDir)
      throws Exception {
    // Does download and install from URL
    super(installDir, version.downloadURL());

    this.config = config;
    this.version = version;

    // Get tomcat module path
    tomcatModulePath = findAndExtractModule(GEODE_BUILD_HOME, "tomcat");
    // Default properties
    systemProperties.put("cache-xml-file", tomcatModulePath + "/conf/" + config.getXMLFile());
    cacheProperties.put("enableLocalCache", "false");

    // Install geode sessions into tomcat install
    installGeodeSessions();

    // Add the needed XML tags
    updateXMLFiles();
    // Add required jars copied to jar skips so container startup is faster
    if (version.jarSkipPropertyName() != null) {
      updateProperties();
    }
  }

  private void installGeodeSessions()
      throws IOException {
    String extraJarsDir = GEODE_BUILD_HOME + "/lib/";

    System.out.println("Found tomcat module " + tomcatModulePath);
    System.out.println("Using extra jar directory " + extraJarsDir);

    // Copy the required jar files to the tomcat install
    copyTomcatGeodeReqFiles(getInstallPath() + "/lib/", extraJarsDir);
  }

  private void copyTomcatGeodeReqFiles(String tomcatLibPath, String extraJarsPath) throws IOException {
    ArrayList<File> requiredFiles = new ArrayList<>();

    // List of required jars and form version regexps from them
    String versionRegex = "-[0-9]+.*\\.jar";
    ArrayList<Pattern> patterns = new ArrayList<>(tomcatRequiredJars.length);
    for (String jar : tomcatRequiredJars)
      patterns.add(Pattern.compile(jar + versionRegex));

    // Don't need to copy any jars already in the tomcat install
    File tomcatLib = new File(tomcatLibPath);
    if (tomcatLib.exists()) {
      try {
        for (File file : tomcatLib.listFiles())
          patterns.removeIf(pattern -> pattern.matcher(file.getName()).find());
      } catch (NullPointerException e) {
        throw new IOException("No files found in tomcat lib directory " + tomcatLibPath);
      }
    } else {
      tomcatLib.mkdir();
    }

    // Find all the required jars in the tomcatModulePath
    try {
      for (File file : (new File(tomcatModulePath + "/lib/")).listFiles()) {
        for (Pattern pattern : patterns) {
          if (pattern.matcher(file.getName()).find()) {
            requiredFiles.add(file);
            patterns.remove(pattern);
            break;
          }
        }
      }
    } catch (NullPointerException e) {
      throw new IOException(
          "No files found in tomcat module directory " + tomcatModulePath + "/lib/");
    }

    // Find all the required jars in the extraJarsPath
    try {
      for (File file : (new File(extraJarsPath)).listFiles()) {
        for (Pattern pattern : patterns) {
          if (pattern.matcher(file.getName()).find()) {
            requiredFiles.add(file);
            patterns.remove(pattern);
            break;
          }
        }
      }
    } catch (NullPointerException e) {
      throw new IOException("No files found in extra jars directory " + extraJarsPath);
    }

    // Copy the required jars to the given tomcat lib folder
    for (File file : requiredFiles) {
      Files.copy(file.toPath(), tomcatLib.toPath().resolve(file.toPath().getFileName()),
          StandardCopyOption.REPLACE_EXISTING);
      System.out.println("Copied required jar from " + file.toPath() + " to "
          + (new File(tomcatLibPath)).toPath().resolve(file.toPath().getFileName()));
    }
  }

  private void editPropertyFile(String filePath, String propertyName, String propertyValue,
      boolean append) throws Exception {
    FileInputStream input = new FileInputStream(filePath);
    Properties properties = new Properties();
    properties.load(input);

    String val;
    if (append)
      val = properties.getProperty(propertyName) + propertyValue;
    else
      val = propertyValue;

    properties.setProperty(propertyName, val);
    properties.store(new FileOutputStream(filePath), null);
  }

  private void updateProperties() throws Exception {
    String jarsToSkip = "";
    for (String jarName : tomcatRequiredJars)
      jarsToSkip += "," + jarName + "*.jar";

    editPropertyFile(getInstallPath() + "/conf/catalina.properties", version.jarSkipPropertyName(),
        jarsToSkip, true);
  }

  private HashMap<String, String> buildServerXMLAttributes() throws IOException {
    HashMap<String, String> attributes = config.getRequiredXMLAttributes();

    for (String property : systemProperties.keySet())
      attributes.put(property, systemProperties.get(property));

    return attributes;
  }

  private HashMap<String, String> buildContextXMLAttributes()
  {
    HashMap<String, String> attributes = version.getRequiredXMLAttributes();

    for (String property : cacheProperties.keySet())
      attributes.put(property, cacheProperties.get(property));

    return attributes;
  }

  private void updateXMLFiles() throws Exception {
    editXMLFile(getInstallPath() + "/conf/server.xml", "Tomcat", "Listener", "Server",
        buildServerXMLAttributes());
    editXMLFile(getInstallPath() + "/conf/context.xml", "Tomcat", "Manager", "Context",
        buildContextXMLAttributes());
  }

  @Override
  public void setLocator(String address, int port) throws Exception {
    if (config == TomcatConfig.CLIENT_SERVER)
    {
      HashMap<String, String> attributes = new HashMap<>();
      attributes.put("host", address);
      attributes.put("port", Integer.toString(port));

      editXMLFile(tomcatModulePath + "/conf/" + config.getXMLFile(), "locator", "pool", attributes, true);
    }
    else {
      systemProperties.put("locators", address + "[" + port + "]");
    }

    updateXMLFiles();
  }

  public TomcatVersion getVersion() {
    return version;
  }

  @Override
  public String getContainerId() {
    return "tomcat" + version.toInteger() + "x";
  }

  @Override
  public String getContainerDescription() {
    return version.name() + "_" + config.name();
  }

  @Override
  public void modifyConfiguration(LocalConfiguration configuration) {
    // Copy context.xml file for actual server to get DeltaSessionManager as manager
    FileConfig contextConfigFile = new FileConfig();
    contextConfigFile.setToDir("conf");
    contextConfigFile.setFile(getInstallPath() + "/conf/context.xml");
    configuration.setConfigFileProperty(contextConfigFile);
  }
}
