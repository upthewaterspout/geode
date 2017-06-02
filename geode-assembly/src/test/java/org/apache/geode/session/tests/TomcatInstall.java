package org.apache.geode.session.tests;

import org.apache.commons.io.FilenameUtils;
import org.apache.geode.management.internal.configuration.utils.ZipUtils;
import org.codehaus.cargo.container.installer.Installer;
import org.codehaus.cargo.container.installer.ZipURLInstaller;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.awt.Container;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Created by danuta on 6/1/17.
 */
public class TomcatInstall extends ContainerInstall
{
  private static final String[] tomcatRequiredJars = { "antlr", "commons-lang", "fastutil", "geode-core", "geode-modules", "javax.transaction-api", "jgroups", "log4j-api", "log4j-core", "log4j-jul", "shiro-core", "slf4j-api", "slf4j-jdk14" };

  private static final String GEODE_BUILD_HOME= System.getenv("GEODE_HOME");

  private TomcatConfig config;
  private final TomcatVersion version;

  private final String INSTALL_PATH;
  private static final String DEFAULT_INSTALL_DIR = "/tmp/cargo_containers/";

  public enum TomcatVersion
  {
    TOMCAT6, TOMCAT7, TOMCAT8, TOMCAT9;

    public String downloadURL()
    {
      switch (this)
      {
        case TOMCAT6:
          return "http://apache.mirrors.lucidnetworks.net/tomcat/tomcat-6/v6.0.53/bin/apache-tomcat-6.0.53.zip";
        case TOMCAT7:
          return "http://repo1.maven.org/maven2/org/apache/tomcat/tomcat/7.0.68/tomcat-7.0.68.zip";
        case TOMCAT8:
          return "http://mirrors.ibiblio.org/apache/tomcat/tomcat-8/v8.5.15/bin/apache-tomcat-8.5.15.zip";
        case TOMCAT9:
          return "http://download.nextag.com/apache/tomcat/tomcat-9/v9.0.0.M21/bin/apache-tomcat-9.0.0.M21.zip";
        default:
          throw new IllegalArgumentException("Illegal tomcat version option");
      }
    }

    public int toInteger()
    {
      switch (this)
      {
        case TOMCAT6:
          return 6;
        case TOMCAT7:
          return 7;
        case TOMCAT8:
          return 8;
        case TOMCAT9:
          return 9;
        default:
          throw new IllegalArgumentException("Illegal tomcat version option");
      }
    }

    public HashMap<String, String> getXMLAttributes()
    {
      HashMap<String, String> attributes = new HashMap<>();
      attributes.put("className", "org.apache.geode.modules.session.catalina.Tomcat" + this.toInteger() + "DeltaSessionManager");
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
    this.config = config;
    this.version = version;

    System.out.println("Installing tomcat from URL " + version.downloadURL());
    // Optional step to install the container from a URL pointing to its distribution
    Installer installer = new ZipURLInstaller(
        new URL(version.downloadURL()), "/tmp/downloads", installDir);
    installer.install();
    System.out.println("Installed tomcat into " + installDir);

    INSTALL_PATH = installer.getHome();
    installGeodeSessions(INSTALL_PATH, GEODE_BUILD_HOME);

    // Add the needed XML tags
    updateXMLFiles();
  }

  private void installGeodeSessions(String tomcatInstallPath, String geodeBuildHome) throws Exception
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

  private void copyTomcatGeodeReqFiles(String tomcatLibPath, String tomcatModulePath, String extraJarsPath) throws Exception
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

  private void updateXMLFiles(String locators) throws Exception
  {

    editXMLFile(INSTALL_PATH + "/conf/server.xml", config.getXMLTag(),"Listener", "Server", config.getXMLAttributes(locators));
    editXMLFile(INSTALL_PATH + "/conf/context.xml", config.getXMLTag(),"Manager", "Context", version.getXMLAttributes());
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

  public void setLocators(String locators) throws Exception
  {
    updateXMLFiles(locators);
  }

  public TomcatConfig getConfig()
  {
    return config;
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
  public String getInstallPath()
  {
    return INSTALL_PATH;
  }

  @Override
  public String getContainerDescription()
  {
    return version.name() + "_" + config.name();
  }
}
