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
import java.util.Properties;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class TomcatInstall extends ContainerInstall
{
  private static final String[] tomcatRequiredJars = { "antlr", "commons-lang", "fastutil", "geode-core", "geode-modules", "geode-modules-tomcat7", "geode-modules-tomcat8", "javax.transaction-api", "jgroups", "log4j-api", "log4j-core", "log4j-jul", "shiro-core", "slf4j-api", "slf4j-jdk14" };

  private static final String GEODE_BUILD_HOME= System.getenv("GEODE_HOME");

  private TomcatConfig config;
  private final TomcatVersion version;

  public enum TomcatVersion {
    TOMCAT6(6, "http://archive.apache.org/dist/tomcat/tomcat-6/v6.0.37/bin/apache-tomcat-6.0.37.zip"),
    TOMCAT7(7, "http://archive.apache.org/dist/tomcat/tomcat-7/v7.0.73/bin/apache-tomcat-7.0.73.zip"),
    TOMCAT8(8, "http://archive.apache.org/dist/tomcat/tomcat-8/v8.5.15/bin/apache-tomcat-8.5.15.zip"),
    TOMCAT9(9, "http://archive.apache.org/dist/tomcat/tomcat-9/v9.0.0.M21/bin/apache-tomcat-9.0.0.M21.zip");

    private final int version;
    private final String downloadURL;

    TomcatVersion(int version, String downloadURL) {
      this.version = version;
      this.downloadURL = downloadURL;
    }

    public String downloadURL()
    {
      return downloadURL;
    }

    public int toInteger()
    {
      return version;
    }

    public String jarSkipPropertyName()
    {
      switch (this)
      {
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

    public HashMap<String, String> getXMLAttributes()
    {
      HashMap<String, String> attributes = new HashMap<>();
      int sessionManagerNum;
      switch (this)
      {
        case TOMCAT9:
          sessionManagerNum = 8;
          break;
        default:
          sessionManagerNum = this.toInteger();
      }
      attributes.put("className", "org.apache.geode.modules.session.catalina.Tomcat" + sessionManagerNum + "DeltaSessionManager");
      return attributes;
    }
  }

  public enum TomcatConfig
  {
    PEER_TO_PEER, CLIENT_SERVER;

    private String getXMLClassName()
    {
      switch (this)
      {
        case PEER_TO_PEER:
          return "org.apache.geode.modules.session.catalina.PeerToPeerCacheLifecycleListener";
        case CLIENT_SERVER:
          return "org.apache.geode.modules.session.catalina.ClientServerCacheLifecycleListener";
        default:
          throw new IllegalArgumentException("Illegal tomcat config option");
      }
    }

    public HashMap<String, String> getXMLAttributes(String locators)
    {
      HashMap<String, String> attributes = new HashMap<>();
      attributes.put("className", getXMLClassName());
      if (this.equals(PEER_TO_PEER))
        attributes.put("locators", locators);
      else if (locators != null && !locators.equals(""))
        throw new IllegalArgumentException("Illegal tomcat config option. Client Servers do not take locators");

      return attributes;
    }

    public String getXMLTag()
    {
      return "Tomcat";
    }
  }

  public TomcatInstall(TomcatVersion version) throws Exception
  {
    this(version, TomcatConfig.PEER_TO_PEER, DEFAULT_INSTALL_DIR);
  }

  public TomcatInstall(TomcatVersion version, String installDir) throws Exception
  {
    this(version, TomcatConfig.PEER_TO_PEER, installDir);
  }

  public TomcatInstall(TomcatVersion version, TomcatConfig config) throws Exception
  {
    this(version, config, DEFAULT_INSTALL_DIR);
  }

  private TomcatInstall(TomcatVersion version, TomcatConfig config, String installDir) throws Exception
  {
    super(installDir, version.downloadURL());

    this.config = config;
    this.version = version;

    installGeodeSessions(getInstallPath(), GEODE_BUILD_HOME);

    // Add the needed XML tags
    updateXMLFiles();
    // Add required jars copied to jar skips so container startup is faster
    if (version.jarSkipPropertyName() != null) {
      updateProperties();
    }
  }

  private void installGeodeSessions(String tomcatInstallPath, String geodeBuildHome) throws IOException
  {
    String extraJarsDir = geodeBuildHome + "/lib/";
    String modulesDir = geodeBuildHome + "/tools/Modules/";

    boolean archive = false;
    String tomcatModulePath = null;

    System.out.println("Trying to access build dir " + modulesDir);
    // Search directory for tomcat module folder/zip
    for (File file : (new File(modulesDir)).listFiles()) {
      if (file.getName().toLowerCase().contains("tomcat")) {
        tomcatModulePath = file.getAbsolutePath();

        archive = !file.isDirectory();
        if (!archive)
          break;
      }
    }

    // Unzip if it is a zip file
    if (archive) {
      if (!FilenameUtils.getExtension(tomcatModulePath).equals("zip"))
        throw new IOException("Bad tomcat module archive " + tomcatModulePath);

      ZipUtils.unzip(tomcatModulePath, tomcatModulePath.substring(0, tomcatModulePath.length() - 4));
      System.out.println("Unzipped " + tomcatModulePath + " into " + tomcatModulePath.substring(0, tomcatModulePath.length() - 4));

      tomcatModulePath = tomcatModulePath.substring(0, tomcatModulePath.length() - 4);
    }

    // No module found within directory throw IOException
    if (tomcatModulePath == null)
      throw new IOException("No tomcat module found in " + modulesDir);

    System.out.println("Found tomcat module " + tomcatModulePath);
    System.out.println("Using extra jar directory " + extraJarsDir);

    // Copy the required jar files to the tomcat install
    copyTomcatGeodeReqFiles(tomcatInstallPath + "/lib/", tomcatModulePath, extraJarsDir);
  }

  private void copyTomcatGeodeReqFiles(String tomcatLibPath, String tomcatModulePath, String extraJarsPath) throws IOException
  {
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
    }
    else
      tomcatLib.mkdir();

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
      throw new IOException("No files found in tomcat module directory " + tomcatModulePath + "/lib/");
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
    for (File file : requiredFiles)
    {
      Files.copy(file.toPath(), tomcatLib.toPath().resolve(file.toPath().getFileName()), StandardCopyOption.REPLACE_EXISTING);
      System.out.println("Copied required jar from " + file.toPath() + " to " + (new File(tomcatLibPath)).toPath().resolve(file.toPath().getFileName()));
    }
  }

  private void editPropertyFile(String filePath, String propertyName, String propertyValue, boolean append) throws Exception
  {
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

  private void editXMLFile(String XMLPath, String tagId, String tagName, String parentTagName, HashMap<String, String> attributes) throws Exception
  {
    // Get XML file to edit
    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
    Document doc = docBuilder.parse(XMLPath);

    boolean hasTag = false;
    NodeList nodes = doc.getElementsByTagName(tagName);

    // If tags with name were found search to find tag with proper tagId and update its fields
    if (nodes != null) {
      for (int i = 0; i < nodes.getLength(); i++) {
        Node node = nodes.item(i);
        Node idAttr = node.getAttributes().getNamedItem("id");
        // Check node for id attribute
        if (idAttr != null && idAttr.getTextContent().equals(tagId)) {
          for (String key : attributes.keySet())
            node.getAttributes().getNamedItem(key).setTextContent(attributes.get(key));

          hasTag = true;
          break;
        }
      }
    }

    if (!hasTag)
    {
      Element e = doc.createElement(tagName);
      // Set id attribute
      e.setAttribute("id", tagId);
      // Set other attributes
      for (String key : attributes.keySet())
        e.setAttribute(key, attributes.get(key));

      //WordUtils.capitalize(FilenameUtils.getBaseName(XMLPath))
      // Add it as a child of the tag for the file
      doc.getElementsByTagName(parentTagName).item(0).appendChild(e);
    }

    // Write updated XML file
    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Transformer transformer = transformerFactory.newTransformer();
    DOMSource source = new DOMSource(doc);
    StreamResult result = new StreamResult(new File(XMLPath));
    transformer.transform(source, result);

    System.out.println("Modified tomcat XML file " + XMLPath);
  }

  private void updateProperties() throws Exception
  {
    String jarsToSkip = "";
    for (String jarName : tomcatRequiredJars)
      jarsToSkip += "," + jarName + "*.jar";

    editPropertyFile(getInstallPath() + "/conf/catalina.properties", version.jarSkipPropertyName(), jarsToSkip, true);
  }

  private void updateXMLFiles(String locators) throws Exception
  {
    editXMLFile(getInstallPath() + "/conf/server.xml", config.getXMLTag(),"Listener", "Server", config.getXMLAttributes(locators));
    editXMLFile(getInstallPath() + "/conf/context.xml", config.getXMLTag(),"Manager", "Context", version.getXMLAttributes());
  }

  private void updateXMLFiles() throws Exception
  {
    updateXMLFiles("");
  }

  public void setConfiguration(TomcatConfig config) throws Exception
  {
    this.config = config;
    updateXMLFiles();
  }

  @Override
  public void setLocators(String locators) throws Exception
  {
    updateXMLFiles(locators);
  }

  public TomcatVersion getVersion()
  {
    return version;
  }

  @Override
  public String getContainerId()
  {
    return "tomcat" + version.toInteger() + "x";
  }

  @Override
  public String getContainerDescription()
  {
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

  @Override
  public WAR getDeployableWAR()
  {
    // Start out searching directory above current
    String curPath = "../";

    // Looking for extensions folder
    final String warModuleDirName = "extensions";
    File warModuleDir = null;

    // While directory searching for is not found
    while (warModuleDir == null)
    {
      // Try to find the find the directory in the current directory
      File[] files = new File(curPath).listFiles();
      for (File file : files)
      {
        if (file.isDirectory() && file.getName().equals(warModuleDirName))
        {
          warModuleDir = file;
          break;
        }
      }

      // Keep moving up until you find it
      curPath += "../";
    }

    // Return path to extensions plus hardcoded path from there to the WAR
    return new WAR(warModuleDir.getAbsolutePath() + "/session-testing-war/build/libs/session-testing-war.war");
  }
}
