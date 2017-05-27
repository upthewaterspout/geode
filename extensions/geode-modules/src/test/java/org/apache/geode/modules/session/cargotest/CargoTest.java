package org.apache.geode.modules.session.cargotest;

import static org.junit.Assert.assertEquals;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import org.apache.geode.modules.session.QueryCommand;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.codehaus.cargo.container.ContainerType;
import org.codehaus.cargo.container.InstalledLocalContainer;
import org.codehaus.cargo.container.configuration.Configuration;
import org.codehaus.cargo.container.configuration.ConfigurationType;
import org.codehaus.cargo.container.configuration.LocalConfiguration;
import org.codehaus.cargo.container.deployable.WAR;
import org.codehaus.cargo.container.installer.Installer;
import org.codehaus.cargo.container.installer.ZipURLInstaller;
import org.codehaus.cargo.container.property.GeneralPropertySet;
import org.codehaus.cargo.container.property.LoggingLevel;
import org.codehaus.cargo.container.tomcat.internal.Tomcat8x9xConfigurationBuilder;
import org.codehaus.cargo.generic.DefaultContainerFactory;
import org.codehaus.cargo.generic.configuration.DefaultConfigurationFactory;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by danuta on 5/26/17.
 */
public class CargoTest
{
  private static final String TOMCAT7_URL = "http://repo1.maven.org/maven2/org/apache/tomcat/tomcat/7.0.68/tomcat-7.0.68.zip";
  private static final String TOMCAT85_URL = "http://mirrors.sonic.net/apache/tomcat/tomcat-8/v8.5.15/src/apache-tomcat-8.5.15-src.zip";

  InstalledLocalContainer container = null;

  public void setupContainer(String containerSourceUrl) throws MalformedURLException
  {
    System.out.println("Installing...");
    // Optional step to install the container from a URL pointing to its distribution
    Installer installer = new ZipURLInstaller(
        new URL(containerSourceUrl), "/tmp/downloads", "/tmp/tomcat7");
    installer.install();

    System.out.println("Configuring...");
    // Create the Cargo Container instance wrapping our physical container
    LocalConfiguration configuration = (LocalConfiguration) new DefaultConfigurationFactory().createConfiguration(
        "tomcat7x", ContainerType.INSTALLED, ConfigurationType.STANDALONE);
    configuration.setProperty(GeneralPropertySet.LOGGING, LoggingLevel.HIGH.getLevel());

    System.out.println("Deploying war...");
    // Statically deploy some WAR (optional)
    WAR war = new WAR("/Users/danuta/Documents/geode/extensions/test-war/build/libs/test-war-1.2.0-SNAPSHOT.war");
    war.setContext("");
    configuration.addDeployable(war);

    System.out.println("Setting up container...");
    container =
        (InstalledLocalContainer) (new DefaultContainerFactory()).createContainer(
            "tomcat7x", ContainerType.INSTALLED, configuration);

    container.setHome(installer.getHome());
    container.setOutput("/tmp/output.log");
  }

  @Test
  public void testCargo() throws Exception
  {
    setupContainer(TOMCAT7_URL);
    System.out.println("Container setup");

    container.start();
    System.out.println(container.getOutput());
    System.out.println("Started container");

    CloseableHttpClient httpclient = HttpClients.createDefault();
    CloseableHttpResponse
        response =
        httpclient.execute(new HttpGet(String.format("http://localhost:%d/awesome", 8080)));

    System.out.println(response.toString());
    System.out.println(EntityUtils.toString(response.getEntity()));

    container.stop();
    System.out.println("Stopped container");
  }

}
