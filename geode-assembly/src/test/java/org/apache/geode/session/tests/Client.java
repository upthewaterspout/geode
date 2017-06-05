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
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Created by danuta on 6/5/17.
 */
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
    BasicClientCookie cookie = new BasicClientCookie("JSESSIONID", this.cookie);
    cookie.setDomain(req.getURI().getHost());
    cookie.setPath("/");

    BasicCookieStore cookieStore = new BasicCookieStore();
    cookieStore.addCookie(cookie);

    context.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);

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
