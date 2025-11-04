package de.dfki.mlt.drz.mkm.nlu;

import static de.dfki.mlt.drz.mkm.nlu.Constants.KEY_HOST;
import static de.dfki.mlt.drz.mkm.nlu.Constants.KEY_PORT;
import static de.dfki.mlt.drz.mkm.nlu.Constants.TRANSCRIPT_NEW_LABEL;
import static de.dfki.mlt.drz.mkm.nlu.Constants.TRANSCRIPT_OLD_LABEL;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dfki.mlt.rudimant.agent.nlp.DialogueAct;
import de.dfki.mlt.rudimant.agent.nlp.Interpreter;

/** This module can be activated by specifying appropriate config parameters.
 *
 *  This Interpreter only uses the slot extraction from the BERT intent and
 *  slot server based on adapters
 *
 * Connects to the drz_intentslot server that does intent recognition and
 *    detection of the following slots:
 *    "einheit", "auftrag", "mittel", "ziel", "weg"
 */
public abstract class BertSlotInterpreter extends Interpreter {
  static final Logger log = LoggerFactory.getLogger(BertSlotInterpreter.class);
  protected String transcription_old = null;
  protected boolean ready = false;

  protected RESTInterpreter ri = new RESTInterpreter();
  private static final String SLOT_ENDPOINT = "annotate_slots";

  public boolean connect() {
    boolean isReady = false;
    try {
      if (ri.connect()) {
        log.info("Adapter intent and slot recognition connected");
        isReady = true;
      }
    } catch (IOException e) {
      log.error("Error connecting adapter intent and slot recognition: {}", e);
    }
    return isReady;
  }

  @Override
  @SuppressWarnings("rawtypes")
  public boolean init(File configDir, String language, Map config) {
    name = "SLOT_" + language;
    ri.predictEndpoint = "annotate";
    boolean result = super.init(configDir, language, config);
    if (! result) return result;
    if (config.containsKey(KEY_HOST)) {
      ri.host = (String) config.get(KEY_HOST);
    }
    if (config.containsKey(KEY_PORT)) {
      ri.port =  (int)config.get(KEY_PORT);
    }

    // connect will be lazy to avoid losing the interpreter when the
    // server's startup is not yet completed
    //ready = connect();
    return true;
  }

  protected JSONObject classify(String transcript_new, String transcript_old,
      String endpoint)
      throws IOException {
    if (! ready) {
      if (! connect()) return null;
    }
    // TODO: generalise: maybe pass a <String, String> dict?
    Map<String, String> params = new HashMap<>();
    params.put(TRANSCRIPT_NEW_LABEL, transcript_new);
    //params.put(TRANSCRIPT_OLD_LABEL, transcript_old);
    return ri.classify_get(params, endpoint);
  }


  /**
   * @param text the string to analyse
   * @return a converted response from the intent recognition
   */
  public DialogueAct analyseSlots(String text) {
    JSONObject json;
    if (transcription_old != null && transcription_old.isBlank()) {
      transcription_old = text;
    }
    if (text == null || text.isEmpty()) {
      json = new JSONObject();
    } else {
      try {
        json = classify(text, transcription_old, SLOT_ENDPOINT);
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
