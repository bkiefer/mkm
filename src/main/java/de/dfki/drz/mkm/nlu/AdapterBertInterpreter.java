package de.dfki.drz.mkm.nlu;

import static de.dfki.drz.mkm.nlu.Constants.KEY_HOST;
import static de.dfki.drz.mkm.nlu.Constants.KEY_PORT;
import static de.dfki.drz.mkm.nlu.Constants.TRANSCRIPT_NEW_LABEL;
import static de.dfki.drz.mkm.nlu.Constants.TRANSCRIPT_OLD_LABEL;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dfki.mlt.rudimant.agent.nlp.DialogueAct;
import de.dfki.mlt.rudimant.agent.nlp.Interpreter;

public class AdapterBertInterpreter extends Interpreter {
  static final Logger log = LoggerFactory.getLogger(AdapterBertInterpreter.class);

  boolean ready = false;

  private RESTInterpreter ri = new RESTInterpreter();

  private String transcription_old = "";


  @Override
  @SuppressWarnings("rawtypes")
  public boolean init(File configDir, String language, Map config) {
    name = "DA_SLOT_" + language;
    ri.predictEndpoint = "annotate";
    boolean result = super.init(configDir, language, config);
    if (! result) return result;
    if (config.containsKey(KEY_HOST)) {
      ri.host = (String) config.get(KEY_HOST);
    }
    if (config.containsKey(KEY_PORT)) {
      ri.port =  (int)config.get(KEY_PORT);
    }

    try {
      if (ri.connect()) {
        log.info("Adapter intent and slot recognition connected");
        ready = true;
      }
    } catch (IOException e) {
      log.error("Error connecting adapter intent and slot recognition: {}", e);
    }
    return ready;
  }

  protected JSONObject classify(String transcript_new, String transcript_old)
      throws IOException {
    // TODO: generalise: maybe pass a <String, String> dict?
    Map<String, String> params = new HashMap<>();
    params.put(TRANSCRIPT_NEW_LABEL, transcript_new);
    //params.put(TRANSCRIPT_OLD_LABEL, transcript_old);
    return ri.classify_get(params);
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
        if (json == null) {
          log.error("Error calling BERT intent recognition: NULL");
          return null;
        }
        log.info("Incoming JSON from " + name + " " + json.toString());
      } catch (IOException e) {
        log.error("Error calling BERT intent recognition: {}", e);
        json = new JSONObject();
      }
      transcription_old = text;
    }
    log.debug("Classified: {} as {}", text, json);
    json.remove("success");
    DialogueAct r = null;
    if (json.has("dialogue_act")) {
      r = convert(json);
    }
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
