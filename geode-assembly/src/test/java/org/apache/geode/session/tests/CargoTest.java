package org.apache.geode.session.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.codehaus.cargo.container.ContainerType;
import org.codehaus.cargo.container.InstalledLocalContainer;
import org.codehaus.cargo.container.configuration.ConfigurationType;
import org.codehaus.cargo.container.configuration.LocalConfiguration;
import org.codehaus.cargo.container.deployable.WAR;
import org.codehaus.cargo.container.property.GeneralPropertySet;
import org.codehaus.cargo.container.property.LoggingLevel;
import org.codehaus.cargo.generic.DefaultContainerFactory;
import org.codehaus.cargo.generic.configuration.DefaultConfigurationFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;

/**
 * Created by danuta on 5/26/17.
 */
public class CargoTest
{
//  private static final String LOG_FILE_PATH = "/tmp/logs/cargo.log";
//  InstalledLocalContainer container = null;
  ContainerManager manager = new ContainerManager();

  URIBuilder reqURIBuild = new URIBuilder();
  CloseableHttpClient httpclient = HttpClients.createDefault();

  URI reqURI;
  HttpGet req;
  CloseableHttpResponse resp;

  public String getPathToTestWAR() throws IOException
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
    return warModuleDir.getAbsolutePath() + "/session-testing-war/build/libs/session-testing-war.war";
  }

  @Before
  public void startContainers() throws Exception
  {
//    TomcatInstall tomcat = new TomcatInstall(TomcatInstall.TomcatVersion.TOMCAT8);
//    manager.addContainer(tomcat);

//    // Create the Cargo Container instance wrapping our physical container
//    LocalConfiguration configuration = (LocalConfiguration) new DefaultConfigurationFactory().createConfiguration(
//        tomcat.getContainerId(), ContainerType.INSTALLED, ConfigurationType.STANDALONE);
//    configuration.setProperty(GeneralPropertySet.LOGGING, LoggingLevel.LOW.getLevel());
//
//    // Statically deploy WAR file for servlet
//    String WARPath = getPathToTestWAR();
//    WAR war = new WAR(WARPath);
//    war.setContext("");
//    configuration.addDeployable(war);
//    System.out.println("Deployed WAR file found at " + WARPath);
//
//    // Create the container, set it's home dir to where it was installed, and set the its output log
//    container =
//        (InstalledLocalContainer) (new DefaultContainerFactory()).createContainer(
//            tomcat.getContainerId(), ContainerType.INSTALLED, configuration);
//
//    container.setHome(tomcat.getInstallPath());
//    container.setOutput(LOG_FILE_PATH);
//    System.out.println("Sending log file output to " + LOG_FILE_PATH);
//
//    System.out.println("Container has been setup");

    // Start setting up URL for testing the container
//    reqURIBuild.setScheme("http");
//    reqURIBuild.setHost("localhost:8080");
//
//    // Start the container (server)
//    manager.startAllContainers();
//    System.out.println("Started container");
  }

  @After
  public void stopContainers() throws Exception
  {
    // Stop the container (server)
//    manager.stopAllContainers();
//    System.out.println("Stopped container");
  }

  @Test
  public void testServlet() throws Exception
  {
    System.out.println("Started and waiting. Go test...");
  }

  @Test
  public void testCargo() throws Exception
  {
    resp = httpclient.execute(new HttpGet(String.format("http://localhost:%d/awesome", 8080)));

    System.out.println(resp.toString());
    System.out.println(EntityUtils.toString(resp.getEntity()));
  }

  @Test
  public void testMultipleContainers() throws Exception
  {
    TomcatInstall tomcat = new TomcatInstall(TomcatInstall.TomcatVersion.TOMCAT7);
    manager.addContainer(tomcat);
    manager.addContainer(tomcat);

    manager.startAllContainers();

    for (int i = 0; i < manager.numContainers(); i++)
    {
      System.out.println("\nTesting " + manager.getContainerDescription(i) + " located on port " + manager.getContainerPort(i));

      URIBuilder reqURIBuild = new URIBuilder();
      reqURIBuild.setScheme("http");
      reqURIBuild.setHost("localhost:" + manager.getContainerPort(i));
      reqURIBuild.setParameter("cmd", QueryCommand.GET.name());
      reqURIBuild.setParameter("param", "null");

      CloseableHttpClient httpclient = HttpClients.createDefault();
      URI reqURI = reqURIBuild.build();

      HttpGet req = new HttpGet(reqURI);
      CloseableHttpResponse resp = httpclient.execute(req);

      assertEquals("JSESSIONID", resp.getFirstHeader("Set-Cookie").getElements()[0].getName());
    }

    manager.stopAllContainers();
  }

  @Test
  public void testSanity() throws Exception {
    reqURIBuild.setParameter("cmd", QueryCommand.GET.name());
    reqURIBuild.setParameter("param", "null");
    reqURI = reqURIBuild.build();

    req = new HttpGet(reqURI);
    resp = httpclient.execute(req);

//    HeaderElementIterator it = new BasicHeaderElementIterator(
//        response.headerIterator("Set-Cookie"));
//
//    while (it.hasNext()) {
//      HeaderElement elem = it.nextElement();
//      System.out.println(elem.getName() + " = " + elem.getValue());
//      NameValuePair[] params = elem.getParameters();
//      for (int i = 0; i < params.length; i++) {
//        System.out.println(" " + params[i]);
//      }
//    }

//    System.out.println(response);
//    System.out.println(response.toString());
//    System.out.println("Entity: ");
//    System.out.println(EntityUtils.toString(response.getEntity()));
//    System.out.println("Status line: " + response.getStatusLine());
//    for (Header h : response.getAllHeaders())
//      System.out.println(h.getName() + " = " + h.getValue());
//    System.out.println(response.getAllHeaders());
//    System.out.println(response.getFirstHeader("Set-Cookie").getValue().split("=")[0]);
//    for (HeaderElement e : response.getFirstHeader("Set-Cookie").name)
//    {
//      System.out.println(e.getParameterCount());
//    }
//    System.out.println("Cookie");
//    for (HeaderElement e : response.getFirstHeader("Set-Cookie").getElements())
//      System.out.println(e.getName() + " = " + e.getValue());

    assertEquals("JSESSIONID", resp.getFirstHeader("Set-Cookie").getElements()[0].getName());
  }

  /**
   * Check that our session persists. The values we pass in as query params are used to set
   * attributes on the session.
   */
  @Test
  public void testSessionPersists() throws Exception {
    String key = "value_testSessionPersists";
    String value = "Foo";

    reqURIBuild.clearParameters();
    reqURIBuild.setParameter("cmd", QueryCommand.SET.name());
    reqURIBuild.setParameter("param", key);
    reqURIBuild.setParameter("value", value);
    reqURI = reqURIBuild.build();

    req = new HttpGet(reqURI);
    resp = httpclient.execute(req);

    String sessionId = resp.getFirstHeader("Set-Cookie").getElements()[0].getValue();
    assertNotNull("No apparent session cookie", sessionId);

    reqURIBuild.clearParameters();
    reqURIBuild.setParameter("cmd", QueryCommand.GET.name());
    reqURIBuild.setParameter("param", key);
    reqURI = reqURIBuild.build();

    req = new HttpGet(reqURI);
    resp = httpclient.execute(req);

//    for (Header h : response.getAllHeaders())
//    {
//      System.out.println(h.getName());
//      HeaderElementIterator it = new BasicHeaderElementIterator(
//          response.headerIterator(h.getName()));
//
//      while (it.hasNext()) {
//        HeaderElement elem = it.nextElement();
//        System.out.println(elem.getName() + " = " + elem.getValue());
//        NameValuePair[] params = elem.getParameters();
//        for (int i = 0; i < params.length; i++) {
//          System.out.println(" " + params[i]);
//        }
//      }
//    }

    assertEquals(value, EntityUtils.toString(resp.getEntity()));
  }

  /**
   * Test that invalidating a session makes it's attributes inaccessible.
   */
  @Test
  public void testInvalidate() throws Exception {
    String key = "value_testInvalidate";
    String value = "Foo";

    reqURIBuild.clearParameters();
    reqURIBuild.setParameter("cmd", QueryCommand.SET.name());
    reqURIBuild.setParameter("param", key);
    reqURIBuild.setParameter("value", value);
    reqURI = reqURIBuild.build();

    req = new HttpGet(reqURI);
    resp = httpclient.execute(req);

    reqURIBuild.clearParameters();
    reqURIBuild.setParameter("cmd", QueryCommand.INVALIDATE.name());
    reqURI = reqURIBuild.build();

    req = new HttpGet(reqURI);
    resp = httpclient.execute(req);

    reqURIBuild.clearParameters();
    reqURIBuild.setParameter("cmd", QueryCommand.GET.name());
    reqURIBuild.setParameter("param", key);
    reqURI = reqURIBuild.build();

    req = new HttpGet(reqURI);
    resp = httpclient.execute(req);

    assertEquals("", EntityUtils.toString(resp.getEntity()));
  }

  /**
   * Test expiration of a session by the tomcat container, rather than gemfire expiration
   */
  @Test
  public void testSessionExpirationByContainer() throws Exception {
    String key = "value_testSessionExpiration";
    String value = "Foo";

    // Set an attribute
    reqURIBuild.clearParameters();
    reqURIBuild.setParameter("cmd", QueryCommand.SET.name());
    reqURIBuild.setParameter("param", key);
    reqURIBuild.setParameter("value", value);
    reqURI = reqURIBuild.build();

    req = new HttpGet(reqURI);
    resp = httpclient.execute(req);

    // Set the session timeout of this one session.
    reqURIBuild.clearParameters();
    reqURIBuild.setParameter("cmd", QueryCommand.SET_MAX_INACTIVE.name());
    reqURIBuild.setParameter("value", "1");
    reqURI = reqURIBuild.build();

    req = new HttpGet(reqURI);
    resp = httpclient.execute(req);

    // Wait until the session should expire
    Thread.sleep(2000);

    // Do a request, which should cause the session to be expired
    reqURIBuild.clearParameters();
    reqURIBuild.setParameter("cmd", QueryCommand.GET.name());
    reqURIBuild.setParameter("param", key);
    reqURI = reqURIBuild.build();

    req = new HttpGet(reqURI);
    resp = httpclient.execute(req);

    assertEquals("", EntityUtils.toString(resp.getEntity()));
  }

//  /**
//   * Test callback functionality. This is here really just as an example. Callbacks are useful to
//   * implement per test actions which can be defined within the actual test method instead of in a
//   * separate servlet class.
//   */
//  @Test
//  public void testCallback() throws Exception {
//    final String helloWorld = "Hello World";
//    Callback c = new Callback() {
//
//      @Override
//      public void call(HttpServletRequest request, HttpServletResponse response)
//          throws IOException {
//        PrintWriter out = response.getWriter();
//        out.write(helloWorld);
//      }
//    };
//    servlet.getServletContext().setAttribute("callback", c);
//
//    Runtime rt = Runtime.getRuntime();
//    Process pr = rt.exec("jar cvf /path/to/your/project/your-file.war");
//
//    WebConversation wc = new WebConversation();
//    WebRequest req = new GetMethodWebRequest(String.format("http://localhost:%d/test", port));
//
//    req.setParameter("cmd", QueryCommand.CALLBACK.name());
//    req.setParameter("param", "callback");
//    WebResponse response = wc.getResponse(req);
//
//    assertEquals(helloWorld, response.getText());
//  }
}
