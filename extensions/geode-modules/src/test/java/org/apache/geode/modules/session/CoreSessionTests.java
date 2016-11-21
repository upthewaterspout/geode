package org.apache.geode.modules.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import org.apache.geode.cache.Region;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public abstract class CoreSessionTests {
  /**
   * Check that the basics are working
   */
  @Test
  public void testSanity() throws Exception {
    WebConversation wc = new WebConversation();
    WebRequest req = new GetMethodWebRequest(getServletURL());
    req.setParameter("cmd", QueryCommand.GET.name());
    req.setParameter("param", "null");
    WebResponse response = wc.getResponse(req);

    assertEquals("JSESSIONID", response.getNewCookieNames()[0]);
  }

  protected abstract String getServletURL();

  /**
   * Test callback functionality. This is here really just as an example. Callbacks are useful to
   * implement per test actions which can be defined within the actual test method instead of in a
   * separate servlet class.
   */
  @Test
  public void testCallback() throws Exception {
    final String helloWorld = "Hello World";
    Callback c = new Callback() {

      @Override
      public void call(HttpServletRequest request, HttpServletResponse response)
          throws IOException {
        PrintWriter out = response.getWriter();
        out.write(helloWorld);
      }
    };
    setServletCallback(c);

    WebConversation wc = new WebConversation();
    WebRequest req = new GetMethodWebRequest(getServletURL());

    req.setParameter("cmd", QueryCommand.CALLBACK.name());
    req.setParameter("param", "callback");
    WebResponse response = wc.getResponse(req);

    assertEquals(helloWorld, response.getText());
  }

  protected abstract void setServletCallback(Callback c);

  /**
   * Test that calling session.isNew() works for the initial as well as subsequent requests.
   */
  @Test
  public void testIsNew() throws Exception {
    Callback c = new Callback() {

      @Override
      public void call(HttpServletRequest request, HttpServletResponse response)
          throws IOException {
        HttpSession session = request.getSession();
        response.getWriter().write(Boolean.toString(session.isNew()));
      }
    };
    setServletCallback(c);

    WebConversation wc = new WebConversation();
    WebRequest req = new GetMethodWebRequest(getServletURL());

    req.setParameter("cmd", QueryCommand.CALLBACK.name());
    req.setParameter("param", "callback");
    WebResponse response = wc.getResponse(req);

    assertEquals("true", response.getText());
    response = wc.getResponse(req);

    assertEquals("false", response.getText());
  }

  /**
   * Check that our session persists. The values we pass in as query params are used to set
   * attributes on the session.
   */
  @Test
  public void testSessionPersists1() throws Exception {
    String key = "value_testSessionPersists1";
    String value = "Foo";

    WebConversation wc = new WebConversation();
    WebRequest req = new GetMethodWebRequest(getServletURL());
    req.setParameter("cmd", QueryCommand.SET.name());
    req.setParameter("param", key);
    req.setParameter("value", value);
    WebResponse response = wc.getResponse(req);
    String sessionId = response.getNewCookieValue("JSESSIONID");

    assertNotNull("No apparent session cookie", sessionId);

    // The request retains the cookie from the prior response...
    req.setParameter("cmd", QueryCommand.GET.name());
    req.setParameter("param", key);
    req.removeParameter("value");
    response = wc.getResponse(req);

    assertEquals(value, response.getText());
  }

  /**
   * Test that invalidating a session makes it's attributes inaccessible.
   */
  @Test
  public void testInvalidate() throws Exception {
    String key = "value_testInvalidate";
    String value = "Foo";

    WebConversation wc = new WebConversation();
    WebRequest req = new GetMethodWebRequest(getServletURL());

    // Set an attribute
    req.setParameter("cmd", QueryCommand.SET.name());
    req.setParameter("param", key);
    req.setParameter("value", value);
    WebResponse response = wc.getResponse(req);

    // Invalidate the session
    req.removeParameter("param");
    req.removeParameter("value");
    req.setParameter("cmd", QueryCommand.INVALIDATE.name());
    wc.getResponse(req);

    // The attribute should not be accessible now...
    req.setParameter("cmd", QueryCommand.GET.name());
    req.setParameter("param", key);
    response = wc.getResponse(req);

    assertEquals("", response.getText());
  }

  /**
   * Test that removing a session attribute also removes it from the region
   */
  @Test
  public void testRemoveAttribute() throws Exception {
    String key = "value_testRemoveAttribute";
    String value = "Foo";

    WebConversation wc = new WebConversation();
    WebRequest req = new GetMethodWebRequest(getServletURL());

    // Set an attribute
    req.setParameter("cmd", QueryCommand.SET.name());
    req.setParameter("param", key);
    req.setParameter("value", value);
    WebResponse response = wc.getResponse(req);
    String sessionId = response.getNewCookieValue("JSESSIONID");

    // Implicitly remove the attribute
    req.removeParameter("value");
    wc.getResponse(req);

    // The attribute should not be accessible now...
    req.setParameter("cmd", QueryCommand.GET.name());
    req.setParameter("param", key);
    response = wc.getResponse(req);

    assertEquals("", response.getText());
    assertNull(getRegion().get(sessionId).getAttribute(key));
  }

  /**
   * Test that a session attribute gets set into the region too.
   */
  @Test
  public void testBasicRegion() throws Exception {
    String key = "value_testBasicRegion";
    String value = "Foo";

    WebConversation wc = new WebConversation();
    WebRequest req = new GetMethodWebRequest(getServletURL());

    // Set an attribute
    req.setParameter("cmd", QueryCommand.SET.name());
    req.setParameter("param", key);
    req.setParameter("value", value);
    WebResponse response = wc.getResponse(req);
    String sessionId = response.getNewCookieValue("JSESSIONID");

    assertEquals(value, getRegion().get(sessionId).getAttribute(key));
  }

  /**
   * Test that a session attribute gets removed from the region when the session is invalidated.
   */
  @Test
  public void testRegionInvalidate() throws Exception {
    String key = "value_testRegionInvalidate";
    String value = "Foo";

    WebConversation wc = new WebConversation();
    WebRequest req = new GetMethodWebRequest(getServletURL());

    // Set an attribute
    req.setParameter("cmd", QueryCommand.SET.name());
    req.setParameter("param", key);
    req.setParameter("value", value);
    WebResponse response = wc.getResponse(req);
    String sessionId = response.getNewCookieValue("JSESSIONID");

    // Invalidate the session
    req.removeParameter("param");
    req.removeParameter("value");
    req.setParameter("cmd", QueryCommand.INVALIDATE.name());
    wc.getResponse(req);

    assertNull("The region should not have an entry for this session", getRegion().get(sessionId));
  }

  protected abstract Region<String, HttpSession> getRegion();

  /**
   * Test that multiple attribute updates, within the same request result in only the latest one
   * being effective.
   */
  @Test
  public void testMultipleAttributeUpdates() throws Exception {
    final String key = "value_testMultipleAttributeUpdates";
    Callback c = new Callback() {

      @Override
      public void call(HttpServletRequest request, HttpServletResponse response)
          throws IOException {
        HttpSession session = request.getSession();
        for (int i = 0; i < 1000; i++) {
          session.setAttribute(key, Integer.toString(i));
        }
      }
    };
    setServletCallback(c);

    WebConversation wc = new WebConversation();
    WebRequest req = new GetMethodWebRequest(getServletURL());

    // Execute the callback
    req.setParameter("cmd", QueryCommand.CALLBACK.name());
    req.setParameter("param", "callback");
    WebResponse response = wc.getResponse(req);

    String sessionId = response.getNewCookieValue("JSESSIONID");

    assertEquals("999", getRegion().get(sessionId).getAttribute(key));
  }

  /**
   * Test for issue #45 Sessions are being created for every request
   */
  @Test
  public void testExtraSessionsNotCreated() throws Exception {
    Callback c = new Callback() {
      @Override
      public void call(HttpServletRequest request, HttpServletResponse response)
          throws IOException {
        // Do nothing with sessions
        response.getWriter().write("done");
      }
    };
    setServletCallback(c);

    WebConversation wc = new WebConversation();
    WebRequest req = new GetMethodWebRequest(getServletURL());

    // Execute the callback
    req.setParameter("cmd", QueryCommand.CALLBACK.name());
    req.setParameter("param", "callback");
    WebResponse response = wc.getResponse(req);

    assertEquals("done", response.getText());
    assertEquals("The region should be empty", 0, getRegion().size());
  }

  /*
     * Test for issue #38 CommitSessionValve throws exception on invalidated sessions
     */
  @Test
  public void testCommitSessionValveInvalidSession() throws Exception {
    Callback c = new Callback() {
      @Override
      public void call(HttpServletRequest request, HttpServletResponse response)
          throws IOException {
        HttpSession session = request.getSession();
        session.invalidate();
        response.getWriter().write("done");
      }
    };
    setServletCallback(c);

    WebConversation wc = new WebConversation();
    WebRequest req = new GetMethodWebRequest(getServletURL());

    // Execute the callback
    req.setParameter("cmd", QueryCommand.CALLBACK.name());
    req.setParameter("param", "callback");
    WebResponse response = wc.getResponse(req);

    assertEquals("done", response.getText());
  }
}
