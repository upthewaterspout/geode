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

import java.io.IOException;
import java.net.URISyntaxException;

public class Client
{
  private String host = "localhost";
  private int port = 8080;
  private String cookie;
  private HttpContext context;

  private URIBuilder reqURIBuild;
  private CloseableHttpClient httpclient;

  public Client()
  {
    reqURIBuild = new URIBuilder();
    reqURIBuild.setScheme("http");

    httpclient = HttpClients.createDefault();

    context = new BasicHttpContext();

    cookie = null;
  }

  public void setPort(int port)
  {
    this.port = port;
  }
  public int getPort()
  {
    return port;
  }

  public void resetURI()
  {
    reqURIBuild.setHost(host + ":" + port);
    reqURIBuild.clearParameters();
  }

  private CloseableHttpResponse doRequest(HttpGet req) throws IOException
  {
    if (cookie != null) {
      BasicClientCookie cookie = new BasicClientCookie("JSESSIONID", this.cookie);
      cookie.setDomain(req.getURI().getHost());
      cookie.setPath("/");

      BasicCookieStore cookieStore = new BasicCookieStore();
      cookieStore.addCookie(cookie);

      context.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);
    }

    CloseableHttpResponse resp = httpclient.execute(req, context);
    if (this.cookie == null)
      this.cookie = resp.getFirstHeader("Set-Cookie").getElements()[0].getValue();

    return resp;
  }

  public CloseableHttpResponse get(String key) throws IOException, URISyntaxException
  {
    resetURI();
    reqURIBuild.setParameter("cmd", QueryCommand.GET.name());
    reqURIBuild.setParameter("param", key);

    return doRequest(new HttpGet(reqURIBuild.build()));
  }

  public CloseableHttpResponse set(String key, String value) throws IOException, URISyntaxException
  {
    resetURI();
    reqURIBuild.setParameter("cmd", QueryCommand.SET.name());
    reqURIBuild.setParameter("param", key);
    reqURIBuild.setParameter("value", value);

    return doRequest(new HttpGet(reqURIBuild.build()));
  }

  public CloseableHttpResponse invalidate() throws IOException, URISyntaxException
  {
    resetURI();
    reqURIBuild.setParameter("cmd", QueryCommand.INVALIDATE.name());
    reqURIBuild.setParameter("param", "null");

    return doRequest(new HttpGet(reqURIBuild.build()));
  }

  public CloseableHttpResponse setMaxInactive(int time) throws IOException, URISyntaxException
  {
    resetURI();
    reqURIBuild.setParameter("cmd", QueryCommand.SET_MAX_INACTIVE.name());
    reqURIBuild.setParameter("value", Integer.toString(time));

    return doRequest(new HttpGet(reqURIBuild.build()));
  }
}
