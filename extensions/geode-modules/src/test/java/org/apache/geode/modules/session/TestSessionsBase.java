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
package org.apache.geode.modules.session;

import static org.apache.geode.distributed.ConfigurationProperties.*;
import static org.junit.Assert.*;

import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import org.apache.catalina.core.StandardWrapper;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import org.apache.geode.cache.Region;
import org.apache.geode.internal.AvailablePortHelper;
import org.apache.geode.modules.session.catalina.DeltaSessionManager;
import org.apache.geode.modules.session.catalina.PeerToPeerCacheLifecycleListener;

public abstract class TestSessionsBase extends CoreSessionTests {

  private static EmbeddedTomcat server;

  private static Region<String, HttpSession> region;

  private static StandardWrapper servlet;

  protected static DeltaSessionManager sessionManager;

  protected static int port;

  // Set up the servers we need
  public static void setupServer(DeltaSessionManager manager) throws Exception {
    FileUtils.copyDirectory(new File("../resources/test/tomcat"), new File("./tomcat"));
    port = AvailablePortHelper.getRandomAvailableTCPPort();
    server = new EmbeddedTomcat("/test", port, "JVM-1");

    PeerToPeerCacheLifecycleListener p2pListener = new PeerToPeerCacheLifecycleListener();
    p2pListener.setProperty(MCAST_PORT, "0");
    p2pListener.setProperty(LOG_LEVEL, "config");
    server.getEmbedded().addLifecycleListener(p2pListener);
    sessionManager = manager;
    sessionManager.setEnableCommitValve(true);
    server.getRootContext().setManager(sessionManager);

    servlet = server.addServlet("/test/*", "default", CommandServlet.class.getName());
    server.startContainer();

    /*
     * Can only retrieve the region once the container has started up (and the cache has started
     * too).
     */
    region = sessionManager.getSessionCache().getSessionRegion();
  }

  @AfterClass
  public static void teardownClass() throws Exception {
    server.stopContainer();
  }

  /**
   * Reset some data
   */
  @Before
  public void setup() throws Exception {
    sessionManager.setMaxInactiveInterval(30);
    region.clear();
  }

  @Override
  protected String getServletURL() {
    return String.format("http://localhost:%d/test", port);
  }

  @Override
  protected void setServletCallback(final Callback c) {
    servlet.getServletContext().setAttribute("callback", c);
  }

  /**
   * Test setting the session expiration
   */
  @Test
  public void testSessionExpiration1() throws Exception {
    // TestSessions only live for a second
    sessionManager.setMaxInactiveInterval(1);

    String key = "value_testSessionExpiration1";
    String value = "Foo";

    WebConversation wc = new WebConversation();
    WebRequest req = new GetMethodWebRequest(getServletURL());

    // Set an attribute
    req.setParameter("cmd", QueryCommand.SET.name());
    req.setParameter("param", key);
    req.setParameter("value", value);
    WebResponse response = wc.getResponse(req);

    // Sleep a while
    Thread.sleep(65000);

    // The attribute should not be accessible now...
    req.setParameter("cmd", QueryCommand.GET.name());
    req.setParameter("param", key);
    response = wc.getResponse(req);

    assertEquals("", response.getText());
  }

  /**
   * Test setting the session expiration via a property change as would happen under normal
   * deployment conditions.
   */
  @Test
  public void testSessionExpiration2() throws Exception {
    // TestSessions only live for a minute
    sessionManager.propertyChange(new PropertyChangeEvent(server.getRootContext(), "sessionTimeout",
        new Integer(30), new Integer(1)));

    // Check that the value has been set to 60 seconds
    assertEquals(60, sessionManager.getMaxInactiveInterval());
  }

  @Override
  protected Region<String, HttpSession> getRegion() {
    return region;
  }

  /**
   * Test for issue #46 lastAccessedTime is not updated at the start of the request, but only at the
   * end.
   */
  @Test
  public void testLastAccessedTime() throws Exception {
    Callback c = new Callback() {
      @Override
      public void call(HttpServletRequest request, HttpServletResponse response)
          throws IOException {
        HttpSession session = request.getSession();
        // Hack to expose the session to our test context
        session.getServletContext().setAttribute("session", session);
        session.setAttribute("lastAccessTime", session.getLastAccessedTime());
        try {
          Thread.sleep(100);
        } catch (InterruptedException ex) {
        }
        session.setAttribute("somethingElse", 1);
        request.getSession();
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

    HttpSession session = (HttpSession) servlet.getServletContext().getAttribute("session");
    Long lastAccess = (Long) session.getAttribute("lastAccessTime");

    assertTrue(
        "Last access time not set correctly: " + lastAccess.longValue() + " not <= "
            + session.getLastAccessedTime(),
        lastAccess.longValue() <= session.getLastAccessedTime());
  }
}
