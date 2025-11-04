package de.dfki.mlt.drz.mkm.nlu;

import static de.dfki.mlt.drz.mkm.nlu.Constants.KEY_HOST;
import static de.dfki.mlt.drz.mkm.nlu.Constants.KEY_PORT;
import static de.dfki.mlt.drz.mkm.nlu.Constants.TRANSCRIPT_NEW_LABEL;

import java.io.IOException;
import java.util.Map;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.FormBody;

/**
 * @deprecated
 */
public class EinsatzBefehlInterpreter {
  private static final Logger logger = LoggerFactory
      .getLogger(EinsatzBefehlInterpreter.class);

  boolean ready = false;

  private String name = "SlotTagging";

  RESTInterpreter ri = new RESTInterpreter();

  public EinsatzBefehlInterpreter(Map<String, Object> config) {
    ri.host = (String) config.get(KEY_HOST);
    if (config.containsKey(KEY_PORT)) {
      ri.port =  (int)config.get(KEY_PORT);
    }
    try {
      if (ri.connect()) {
        logger.info("BERT Einsatzbefehl recognition connected");
        ready = true;
      }
    } catch (IOException e) {
      logger.error("Error connecting BERT Einsatzbefehl recognition: {}", e);
    }
  }

  protected JSONObject classify(String transcript)
      throws IOException {
    return ri.classify(new FormBody.Builder()
        .add(TRANSCRIPT_NEW_LABEL, transcript)
        .build());
  }
  /**
   * @param text the text to analyse
   * @return the converted result from the Einsatzbefehl module
   */

  public JSONObject analyse(String text) {
//    logger.debug("Received Result for Utterance {} from {}: {}", result.id,
//        result.from, result.transcription);
//    String transcription = result.transcription;
//    Intent intent;
    JSONObject json;
    if (text == null || text.isEmpty()) {
      json = new JSONObject();
    } else {
      try {
        json = classify(text);
      } catch (IOException e) {
        logger.error("Error calling BERT Einsatzbefehl recognition: {}", e);
        json = new JSONObject();
      }
    }

    if (json == null) {
      logger.info("No {} result for {}", name, text);
    } else {
      logger.info("{} result for {}: {}", name, text, json.toString());
    }
    return json;
  }

}
