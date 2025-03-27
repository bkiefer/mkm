package de.dfki.drz.mkm.nlu;

import java.io.IOException;
import java.util.Map;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RESTInterpreter {
  protected static Logger log = LoggerFactory.getLogger(RESTInterpreter.class);
  protected String scheme = "http";
  protected String host = "localhost";
  protected int port = -1;
  protected String aliveEndpoint = "alive";
  protected String predictEndpoint = "predict";

  protected final OkHttpClient client = new OkHttpClient();

  HttpUrl.Builder buildUrl(String endpoint) {
    HttpUrl.Builder b =  new HttpUrl.Builder()
        .scheme(scheme)
        .host(host);
    if (port > 0) {
      b.port(port);
    }
    if (endpoint != null) {
      b.addPathSegment(endpoint);
    }
    return b;
  }

  protected boolean connect() throws IOException {
    Request request = new Request.Builder()
        .url(buildUrl(aliveEndpoint).build())
        .addHeader("Accept", "application/json; charset=utf-8")
        .build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        log.error("Invalid response: {}", response);
        return false;
      }

      // Get response body
      log.debug("HTTP connect body: {}", response.body().string());
    } catch (Exception ex) {
      ex.printStackTrace();
      throw ex;
    }
    return true;
  }

  protected JSONObject getResponse(Request request) throws IOException {
    JSONObject json = null;
    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        log.error("Invalid response: {}", response);
        return null;
      }

      String body = response.body().string();
      // Get response body
      json = new JSONObject(body);
    }
    return json;
  }

  protected JSONObject classify(RequestBody formBody)
      throws IOException {

    Request request = new Request.Builder()
        .url(buildUrl(predictEndpoint).build())
        .addHeader("Accept", "application/json; charset=utf-8")
        .post(formBody)
        .build();

    return getResponse(request);
  }

  protected JSONObject classify_get(Map<String, String> params)
      throws IOException {
    HttpUrl.Builder b = buildUrl(predictEndpoint);
    for (Map.Entry<String, String> e : params.entrySet()) {
      b.addQueryParameter(e.getKey(), e.getValue());
    }

    Request request = new Request.Builder()
        .url(b.build())
        .addHeader("Accept", "application/json; charset=utf-8")
        .method("GET", null)
        .build();

    return getResponse(request);
  }
}
