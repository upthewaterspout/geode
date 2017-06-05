package org.apache.geode.session.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.apache.geode.test.dunit.DUnitEnv;
import org.apache.geode.test.dunit.cache.internal.JUnit4CacheTestCase;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

import java.net.URI;

/**
 * Created by danuta on 5/26/17.
 */
public class CargoTest extends JUnit4CacheTestCase
{
//  private static final String LOG_FILE_PATH = "/tmp/logs/cargo.log";
//  InstalledLocalContainer container = null;
//  ContainerManager manager = new ContainerManager();

  URIBuilder reqURIBuild = new URIBuilder();
  CloseableHttpClient httpclient = HttpClients.createDefault();

  URI reqURI;
  HttpGet req;
  CloseableHttpResponse resp;

  private void containersShouldBeCreatingIndividualSessions(ContainerManager manager) throws Exception
  {
    reqURIBuild = new URIBuilder();
    reqURIBuild.setScheme("http");

    httpclient = HttpClients.createDefault();

    for (int i = 0; i < manager.numContainers(); i++)
    {
      reqURIBuild.setHost("localhost:" + manager.getContainerPort(i));
      reqURIBuild.setParameter("cmd", QueryCommand.GET.name());
      reqURIBuild.setParameter("param", "null");
      reqURI = reqURIBuild.build();

      req = new HttpGet(reqURI);
      resp = httpclient.execute(req);

      assertEquals("JSESSIONID", resp.getFirstHeader("Set-Cookie").getElements()[0].getName());
    }
  }

  private void containersShouldReplicateSessions(ContainerManager manager) throws Exception
  {
    reqURIBuild = new URIBuilder();
    reqURIBuild.setScheme("http");

    if (manager.numContainers() < 2)
      throw new IllegalArgumentException("Bad ContainerManager, must have 2 or more containers for this test");

    reqURIBuild.setHost("localhost:" + manager.getContainerPort(0));
    reqURIBuild.setParameter("cmd", QueryCommand.GET.name());
    reqURIBuild.setParameter("param", "null");

    httpclient = HttpClients.createDefault();
    reqURI = reqURIBuild.build();

    req = new HttpGet(reqURI);
    resp = httpclient.execute(req);

    String cookieString = resp.getFirstHeader("Set-Cookie").getElements()[0].getValue();
    BasicCookieStore cookieStore = new BasicCookieStore();
    BasicClientCookie cookie = new BasicClientCookie("JSESSIONID", cookieString);
    HttpContext context = new BasicHttpContext();

    for (int i = 0; i < manager.numContainers(); i++)
    {
      reqURIBuild.clearParameters();
      reqURIBuild.setHost("localhost:" + manager.getContainerPort(i));
      reqURIBuild.setParameter("cmd", QueryCommand.GET.name());
      reqURIBuild.setParameter("param", "null");

      reqURI = reqURIBuild.build();
      req = new HttpGet(reqURI);

      cookie.setDomain(reqURI.getHost());
      cookie.setPath("/");
      cookieStore.addCookie(cookie);
      context.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);
      resp = httpclient.execute(req, context);

      assertEquals(resp.getFirstHeader("Set-Cookie"), null);
    }
  }

  private void containersShouldHavePersistentSessionData(ContainerManager manager) throws Exception
  {
    String key = "value_testSessionPersists";
    String value = "Foo";

    reqURIBuild = new URIBuilder();
    reqURIBuild.setScheme("http");

    if (manager.numContainers() < 2)
      throw new IllegalArgumentException("Bad ContainerManager, must have 2 or more containers for this test");

    reqURIBuild.setHost("localhost:" + manager.getContainerPort(0));
    reqURIBuild.setParameter("cmd", QueryCommand.SET.name());
    reqURIBuild.setParameter("param", key);
    reqURIBuild.setParameter("value", value);
    reqURI = reqURIBuild.build();

    httpclient = HttpClients.createDefault();

    req = new HttpGet(reqURI);
    resp = httpclient.execute(req);

    String cookieString = resp.getFirstHeader("Set-Cookie").getElements()[0].getValue();
    BasicCookieStore cookieStore = new BasicCookieStore();
    BasicClientCookie cookie = new BasicClientCookie("JSESSIONID", cookieString);
    HttpContext context = new BasicHttpContext();

    for (int i = 0; i < manager.numContainers(); i++)
    {
      reqURIBuild.setHost("localhost:" + manager.getContainerPort(i));
      reqURIBuild.setParameter("cmd", QueryCommand.GET.name());
      reqURIBuild.setParameter("param", key);
      reqURI = reqURIBuild.build();
      req = new HttpGet(reqURI);

      cookie.setDomain(reqURI.getHost());
      cookie.setPath("/");
      cookieStore.addCookie(cookie);
      context.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);
      resp = httpclient.execute(req, context);

      assertEquals(value, EntityUtils.toString(resp.getEntity()));
    }
  }

  private void containerFailureShouldStillAllowOtherContainersDataAccess(ContainerManager manager) throws Exception
  {
    String key = "value_testSessionPersists";
    String value = "Foo";

    reqURIBuild = new URIBuilder();
    reqURIBuild.setScheme("http");

    if (manager.numContainers() < 2)
      throw new IllegalArgumentException("Bad ContainerManager, must have 2 or more containers for this test");

    reqURIBuild.setHost("localhost:" + manager.getContainerPort(0));
    reqURIBuild.setParameter("cmd", QueryCommand.SET.name());
    reqURIBuild.setParameter("param", key);
    reqURIBuild.setParameter("value", value);
    reqURI = reqURIBuild.build();

    httpclient = HttpClients.createDefault();

    req = new HttpGet(reqURI);
    resp = httpclient.execute(req);

    String cookieString = resp.getFirstHeader("Set-Cookie").getElements()[0].getValue();
    BasicCookieStore cookieStore = new BasicCookieStore();
    BasicClientCookie cookie = new BasicClientCookie("JSESSIONID", cookieString);
    HttpContext context = new BasicHttpContext();

    manager.stopContainer(0);

    for (int i = 1; i < manager.numContainers(); i++)
    {
      reqURIBuild.setHost("localhost:" + manager.getContainerPort(i));
      reqURIBuild.setParameter("cmd", QueryCommand.GET.name());
      reqURIBuild.setParameter("param", key);
      reqURI = reqURIBuild.build();
      req = new HttpGet(reqURI);

      cookie.setDomain(reqURI.getHost());
      cookie.setPath("/");
      cookieStore.addCookie(cookie);
      context.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);
      resp = httpclient.execute(req, context);

      assertEquals(value, EntityUtils.toString(resp.getEntity()));
    }
  }

  private void containerInvalidationShouldRemoveValueAccess(ContainerManager manager) throws Exception
  {
    String key = "value_testInvalidate";
    String value = "Foo";

    reqURIBuild = new URIBuilder();
    reqURIBuild.setScheme("http");

    if (manager.numContainers() != 1)
      System.out.println("WARNING: More containers than needed, this test only needs 1 container.");

    reqURIBuild.setHost("localhost:" + manager.getContainerPort(0));
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

  private void containerShouldExpireInSetTimeframe(ContainerManager manager) throws Exception
  {
    String key = "value_testSessionExpiration";
    String value = "Foo";

    reqURIBuild = new URIBuilder();
    reqURIBuild.setScheme("http");

    if (manager.numContainers() != 1)
      System.out.println("WARNING: More containers than needed, this test only needs 1 container.");

    // Set an attribute
    reqURIBuild.setHost("localhost:" + manager.getContainerPort(0));
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

  @Test
  public void twoTomcatContainersShouldBeCreatingIndividualSessions() throws Exception
  {
    TomcatInstall tomcat = new TomcatInstall(TomcatInstall.TomcatVersion.TOMCAT7);
    ContainerManager manager = new ContainerManager();

    manager.addContainer(tomcat);
    manager.addContainer(tomcat);

    manager.startAllInactiveContainers();
    containersShouldBeCreatingIndividualSessions(manager);
    manager.stopAllActiveContainers();
  }

  @Test
  public void twoTomcatContainersShouldReplicateCookies() throws Exception
  {
    TomcatInstall tomcat = new TomcatInstall(TomcatInstall.TomcatVersion.TOMCAT7);
    tomcat.setLocators(DUnitEnv.get().getLocatorString());

    ContainerManager manager = new ContainerManager();
    manager.addContainer(tomcat);
    manager.addContainer(tomcat);

    manager.startAllInactiveContainers();
    containersShouldReplicateSessions(manager);
    manager.stopAllActiveContainers();
  }

  @Test
  public void threeTomcatContainersShouldHavePersistentSessionData() throws Exception
  {
    TomcatInstall tomcat = new TomcatInstall(TomcatInstall.TomcatVersion.TOMCAT7);
    tomcat.setLocators(DUnitEnv.get().getLocatorString());

    ContainerManager manager = new ContainerManager();
    manager.addContainer(tomcat);
    manager.addContainer(tomcat);
    manager.addContainer(tomcat);

    manager.startAllInactiveContainers();
    containersShouldHavePersistentSessionData(manager);
    manager.stopAllActiveContainers();
  }

  @Test
  public void containerFailureShouldStillAllowTwoOtherContainersToAccessSessionData() throws Exception
  {
    TomcatInstall tomcat = new TomcatInstall(TomcatInstall.TomcatVersion.TOMCAT7);
    tomcat.setLocators(DUnitEnv.get().getLocatorString());

    ContainerManager manager = new ContainerManager();
    manager.addContainer(tomcat);
    manager.addContainer(tomcat);
    manager.addContainer(tomcat);

    manager.startAllInactiveContainers();
    containerFailureShouldStillAllowOtherContainersDataAccess(manager);
    manager.stopAllActiveContainers();
  }

  @Test
  public void invalidateShouldNotAllowContainerToAccessKeyValue() throws Exception
  {
    TomcatInstall tomcat = new TomcatInstall(TomcatInstall.TomcatVersion.TOMCAT7);
    tomcat.setLocators(DUnitEnv.get().getLocatorString());

    ContainerManager manager = new ContainerManager();
    manager.addContainer(tomcat);

    manager.startAllInactiveContainers();
    containerInvalidationShouldRemoveValueAccess(manager);
    manager.stopAllActiveContainers();
  }

  @Test
  public void sessionShouldExpireInSetTimePeriod() throws Exception
  {
    TomcatInstall tomcat = new TomcatInstall(TomcatInstall.TomcatVersion.TOMCAT7);
    tomcat.setLocators(DUnitEnv.get().getLocatorString());

    ContainerManager manager = new ContainerManager();
    manager.addContainer(tomcat);

    manager.startAllInactiveContainers();
    containerShouldExpireInSetTimeframe(manager);
    manager.stopAllActiveContainers();
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
