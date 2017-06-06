package org.apache.geode.session.tests;

import org.codehaus.cargo.container.configuration.LocalConfiguration;
import org.codehaus.cargo.container.deployable.WAR;
import org.codehaus.cargo.container.installer.Installer;
import org.codehaus.cargo.container.installer.ZipURLInstaller;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by danuta on 6/5/17.
 */
public class GenericAppServerInstall extends ContainerInstall {

  public enum Server {
    JETTY9("http://central.maven.org/maven2/org/eclipse/jetty/jetty-distribution/9.4.5.v20170502/jetty-distribution-9.4.5.v20170502.zip");

    private String downloadURL;

    Server(String downloadURL) {
      this.downloadURL = downloadURL;
    }

    public String getDownloadURL() {
      return downloadURL;
    }
  }

  public GenericAppServerInstall(Server server) throws MalformedURLException{
    this(DEFAULT_INSTALL_DIR, server);
  }

  public GenericAppServerInstall(String installDir, Server server) throws MalformedURLException{
    super(installDir, server.getDownloadURL());
  }

  @Override
  public String getContainerId() {
    return "jetty9x";
  }

  @Override
  public String getContainerDescription() {
    return null;
  }

  @Override
  public void setLocators(String locators) throws Exception {

  }

  @Override
  public WAR getDeployableWAR()
  {
    return null;
  }
}
