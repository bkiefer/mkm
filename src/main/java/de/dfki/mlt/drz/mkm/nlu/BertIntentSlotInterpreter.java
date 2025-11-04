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
 *  However, since it also produces additional information, the vanilla
 *  CombinedInterpreter might not be the best choice for doing so.
 *
 * Connects to the drz_intentslot server that does intent recognition and
 *    detection of the following slots:
 *    "einheit", "auftrag", "mittel", "ziel", "weg"
 *
 *    The recognized intent set is reduced to the following intents:
 *    Order(top)
 *    Confirm(top)
 *    Disconfirm(top)
 *    Inform(top)
 *    Question(top)
 *    Request(top)
 *    Request(Communication)
 *    Confirm(Communication)
 *   OutOfDomain(top)
 */
public class BertIntentSlotInterpreter extends BertSlotInterpreter {
  static final Logger log = LoggerFactory.getLogger(BertIntentSlotInterpreter.class);

  private static final String INTENT_SLOT_ENDPOINT = "annotate";
  
  @Override
  @SuppressWarnings("rawtypes")
  public boolean init(File configDir, String language, Map config) {
    super.init(configDir, language, config);
    name = "DA_SLOT_" + language;
    transcription_old = "";
    return true;
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
        json = classify(text, transcription_old, INTENT_SLOT_ENDPOINT);
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
