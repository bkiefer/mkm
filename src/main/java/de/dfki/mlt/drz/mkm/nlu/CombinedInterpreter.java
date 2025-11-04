package de.dfki.mlt.drz.mkm.nlu;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.dfki.lt.tr.dialogue.cplan.DagEdge;
import de.dfki.lt.tr.dialogue.cplan.DagNode;
import de.dfki.mlt.rudimant.agent.nlp.DialogueAct;
import de.dfki.mlt.rudimant.agent.nlp.Interpreter;
import de.dfki.mlt.rudimant.agent.nlp.LanguageServices;

/**
 * This class currently implements a sequence of Interpreters to be asked for
 * results. The first that returns a result wins.
 *
 * TODO
 * This class could be extended in several ways (together with Interpreter)
 * 1. Confindence threshold: only consider results with a minimal confidence
 * 2. Alternative parallel Interpreters / ensemble: the highest conf wins
 * To implement this, DialogueAct might have to be extended, or a special slot
 * reserved to store the confidence of the Interpreter result.
 *
 */
public class CombinedInterpreter extends Interpreter {

  List<Interpreter> instances = new ArrayList<>();

  BertSlotInterpreter bsi;

  @SuppressWarnings({ "unchecked", "rawtypes" })
  @Override
  public boolean init(File configDir, String language, Map config) {
    List<Map> interpreterConfigs = (List<Map>) config.get("instances");
    for (Map conf : interpreterConfigs) {
      Interpreter i = (Interpreter) LanguageServices.createNLProcessor(
          configDir, language, conf);
      if (i != null) {
        if (i instanceof BertIntentSlotInterpreter) {
          bsi = (BertSlotInterpreter)i;
        }
        instances.add(i);
      }
    }
    return true;
  }


  boolean isSlotRelevantDA(DialogueAct da) {
    if (da == null) return false;
    switch (da.getDialogueActType()) {
    case "<dial:Order>":
    case "<dial:Inform>":
      return true;
    case "<dial:Request>":
      return ! da.getProposition().equals("Communication");
    }
    return false;
  }

  @Override
  public DialogueAct analyse(String text) {
    logger.info(text);
    try {
      for (Interpreter i : instances) {
        DialogueAct da = i.analyse(text);
        if (da != null) {
          logger.error("Relevant: {}, prop: {} ",
                       isSlotRelevantDA(da) ? "y" : "n",
                       da.getProposition());
          if (! (i instanceof BertIntentSlotInterpreter) && bsi != null
              && isSlotRelevantDA(da)) {
            DialogueAct slotDA = ((BertSlotInterpreter)bsi).analyseSlots(text);
            logger.error("Calling BSI: {}",
                         slotDA != null ? slotDA.toString() : "null");
            String[] slots = {"mittel", "einheit", "aufgabe", "weg", "ziel"};
            for (String slot: slots) {
              short slotId = DagNode.getFeatureId(slot);
              DagEdge e = slotDA.getDag().getEdge(slotId);
              if (e != null) {
                da.getDag().addEdge(slotId, e.getValue());
              }
            }
          }
          return da;
        }
      }
    } catch (Exception ex) {
      logger.error("Exception during NLU: {}\n{}",
                   ex.getMessage(), ex.getStackTrace().toString());
    }
    return noResult();
  }

}
