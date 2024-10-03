package de.dfki.drz.mkm.nlu;

import static de.dfki.drz.mkm.nlu.Constants.*;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dfki.mlt.rudimant.agent.nlp.DialogueAct;
import okhttp3.FormBody;

public class IntentInterpreter extends RESTInterpreter {
  static final Logger log = LoggerFactory.getLogger(IntentInterpreter.class);

  boolean ready = false;

  private String transcription_old = "";

  @Override
  @SuppressWarnings("rawtypes")
  public boolean init(File configDir, String language, Map config) {
    name = "DIT_DIA_" + language;
    boolean result = super.init(configDir, language, config);
    if (! result) return result;
    uri = "http://" + (String) config.get(KEY_HOST)
          + ":" + (int)config.get(KEY_PORT);
    try {
      if (connect()) {
        log.info("BERT intent recognition connected");
        ready = true;
      }
    } catch (IOException e) {
      log.error("Error connecting BERT intent recognition: {}", e);
    }
    return ready;
  }

  protected JSONObject classify(String transcript_new, String transcript_old)
      throws IOException {
    // TODO: generalise: maybe pass a <String, String> dict?
    return classify(new FormBody.Builder()
        .add(TRANSCRIPT_NEW_LABEL, transcript_new)
        .add(TRANSCRIPT_OLD_LABEL, transcript_old)
        .build());
  }


  /**
   * @param text the string to analyse
   * @return a converted response from the intent recognition
   */
  @Override
  public DialogueAct analyse(String text) {
    JSONObject json;
    if (transcription_old.isBlank()) {
      transcription_old = text;
    }
    if (text == null || text.isEmpty()) {
      json = new JSONObject();
    } else {
      try {
        json = classify(text, transcription_old);
      } catch (IOException e) {
        log.error("Error calling BERT intent recognition: {}", e);
        json = new JSONObject();
      }
      transcription_old = text;
    }
    log.debug("Classified: {} as {}", text, json);
    json.remove("success");
    DialogueAct r = convert(json);
    if (r == null) {
      logger.info("No {} result for {}", name, text);
    } else {
      logger.info("{} result for {}: {}", name, text, r.toString());
    }
    return r;
  }

  /** Override only to make the method accessible to test classes */
  @Override
  public DialogueAct convert(JSONObject object) {
    return super.convert(object);
  }
}
