package de.dfki.drz.mkm.nlu;

import java.io.IOException;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dfki.mlt.rudimant.agent.nlp.Interpreter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public abstract class RESTInterpreter extends Interpreter {
  protected static Logger log = LoggerFactory.getLogger(RESTInterpreter.class);
  protected String uri;
  protected String aliveEndpoint = "/alive";
  protected String predictEndpoint = "/predict";
      
  protected final OkHttpClient client = new OkHttpClient();

  protected boolean connect() throws IOException {
    Request request = new Request.Builder()
        .url(uri + aliveEndpoint)
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
        .url(uri + predictEndpoint)
        .addHeader("Accept", "application/json; charset=utf-8")
        .post(formBody)
        .build();

    return getResponse(request);
  }

}
