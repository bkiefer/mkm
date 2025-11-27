package de.dfki.mlt.drz.mkm;

import static de.dfki.mlt.drz.mkm.Constants.INSTANCE_NS_SHORT;
import static de.dfki.lt.tr.dialogue.cplan.DagNode.PROP_FEAT_ID;
import static de.dfki.mlt.rudimant.common.Configs.CFG_ONTOLOGY_FILE;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONWriter;

import de.dfki.lt.hfc.WrongFormatException;
import de.dfki.lt.hfc.db.HfcDbHandler;
import de.dfki.lt.hfc.db.rdfProxy.DbClient;
import de.dfki.lt.hfc.db.rdfProxy.Rdf;
import de.dfki.lt.hfc.db.rdfProxy.RdfProxy;
import de.dfki.lt.hfc.types.XsdAnySimpleType;
import de.dfki.lt.tr.dialogue.cplan.DagEdge;
import de.dfki.lt.tr.dialogue.cplan.DagNode;
import de.dfki.mlt.rudimant.agent.Agent;
import de.dfki.mlt.rudimant.agent.Behaviour;
import de.dfki.mlt.rudimant.agent.nlp.DialogueAct;

public abstract class KnowledgeManager extends Agent {

  protected Rdf user;

  private DbClient handler;

  protected boolean evaluation = false;
  private Writer w = null;

  HfcUtils hu;

  Speaker lastSpeaker = null;

  //private static final SimpleDateFormat sdf =
  //    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

  private RdfProxy startClient(File configDir, Map<String, Object> configs)
      throws IOException, WrongFormatException {
    String ontoFileName = (String) configs.get(CFG_ONTOLOGY_FILE);
    if (ontoFileName == null) {
      throw new IOException("Ontology file is missing.");
    }
    handler = new HfcDbHandler(new File(configDir, ontoFileName).getPath());

    RdfProxy proxy = new RdfProxy(handler);
    hu = new HfcUtils(proxy);
    return proxy;
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  public void init(File configDir, String language, Map configs)
          throws IOException, WrongFormatException {
    RdfProxy proxy = startClient(configDir, configs);
    // the last parameter sets the default namespace for creating instances
    super.init(configDir, language, proxy, configs, INSTANCE_NS_SHORT);
    // log all rules to stdout
    this.logAllRules();
    this.ruleLogger.filterUnchangedRules = false;
    if (configs.containsKey("evaluation") && (boolean)configs.get("evaluation")){
      startEvaluation();
    }

    // start first round of rule evaluations
    newData();
  }

  @Override
  public void shutdown() {
    if (handler != null) ((HfcDbHandler)handler).shutdownNoExit();
    if (w != null) {
      try {
        w.close();
      } catch (IOException ex) {
        logger.error("Closing evaluation file failed: {}", ex.getMessage());
      }
    }
    super.shutdown();
  }

  @Override
  protected Behaviour createBehaviour(int delay, DialogueAct da) {
    System.out.println("Returned DA: " + da.toString());
    return super.createBehaviour(delay, da);
  }

  protected Rdf toRdf(DialogueAct da) {
    return da.toRdf(_proxy);
  }

  private static short firstId = -1, restId;

  private static String getAtom(DagNode dag) {
    return dag.getValue(PROP_FEAT_ID).getTypeName();
  }

  private static DagNode getDag(DagNode dag, String feature) {
    DagEdge e = dag.getEdge(DagNode.getFeatureId(feature));
    return e == null ? null : e.getValue();
  }

  public static List<String> daList(DagNode dag) {
    if (firstId < 0) {
      firstId = DagNode.getFeatureId("first");
      restId = DagNode.getFeatureId("rest");
    }
    List<String> result = new ArrayList<>();
    while (dag != null && dag.getEdge(firstId) != null) {
      String val = getAtom(dag.getValue(firstId));
      result.add(val);
      dag = dag.getValue(restId);
    }
    return result;
  }

  private String na(String in) { return in == null ? "NA" : in; }

  /** remove <> and namespace */
  public static String rdf2name(String uriString) {
    if (!uriString.startsWith("<") || !uriString.endsWith(">"))
      return uriString;
    String us = uriString.substring(1, uriString.length() - 1);
    int i = us.lastIndexOf('#');
    if (i < 0)
      i = us.lastIndexOf(':');
    return us.substring(i + 1);
  }

  Object xsdToJava(String s) {
    try {
      return XsdAnySimpleType.getXsdObject(s).toJava();
    } catch (Exception ex) {
    }
    return null;
  }
  
  long toLong(String s) {
    if (s == null) return 0l;
    Long l = (Long) xsdToJava(s);
    return l == null ? 0l : l.longValue();
  }
  
  String asString(String s) {
    Object sth = xsdToJava(s);
    return sth == null ? s : sth.toString();
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  void sendFusion(DialogueAct da, String sender, String addressee) {
    String id = asString(na(da.getValue("id")));
    String daType = na(da.getDialogueActType());
    String prop = na(da.getProposition());
    String text = na(da.getValue("text"));
    sender = na(sender);
    addressee = na(addressee);
    long fromTime = toLong(da.getValue("fromTime"));
    long toTime= toLong(da.getValue("toTime"));
    Map slots = new HashMap<String, List<String>>();
    DagNode dag = da.getDag();
    // put identified slots into json
    String[] slotnames = { "einheit", "auftrag", "mittel", "weg", "ziel" };
    for (String slot: slotnames) {
      DagNode slotDag = getDag(dag, slot);
      if (slotDag != null) {
        slots.put(slot, daList(slotDag));
      }
    }

    if (evaluation) {
      printEval(da.getValue("id"), sender, addressee, daType, prop, text);
    }

    slots.put("id", id);
    slots.put("sender", sender);
    slots.put("addressee", addressee);
    slots.put("intent", rdf2name(daType));
    slots.put("frame", rdf2name(prop));
    slots.put("text", text);
    slots.put("fromTime", Long.toString(fromTime));
    slots.put("toTime", Long.toString(toTime));
    ((MkmClient)_hub).sendCombined(JSONWriter.valueToString(slots));
  }


  protected void startEvaluation() {
    if (evaluation) return;
    evaluation = true;
    String evalFileName = "evaluation" + System.currentTimeMillis() + ".csv";
    try {
      w = new PrintWriter(new File(evalFileName));
    } catch (FileNotFoundException ex) {
      logger.error("Creating evaluation file failed: {}", ex.getMessage());
      w = null;
      evaluation = false;
    }
  }

  protected void printEval(String id, String speaker, String addressee,
      String daType, String proposition, String text) {
    try {
      if (daType.charAt(0) == '<') {
        int col = daType.indexOf(':');
        daType = daType.substring(col + 1, daType.length() - 1);
      }
      if (proposition != null && !proposition.isBlank()) {
        if (proposition.charAt(0) != '<') {
          daType += '_' + proposition;
        } else {
          if (! proposition.equals("<rdf:top>")) {
            int col = daType.indexOf(':');
            proposition = proposition.substring(col, proposition.length() - 1);
            daType += proposition;
          }
        }
      }

      w.append(na(id) + "," + na(speaker) + "," + na(addressee) + ","
          + na(daType) + ", \"" + na(text) + '"');
      w.append(System.lineSeparator());
      w.flush();
    } catch (IOException ex) {
      logger.error("Writing to evaluation file failed: {}", ex.getMessage());
      try {
        w.close();
      } catch (IOException ex2) {
        logger.error("Closing evaluation file failed: {}", ex2.getMessage());
      }
      w = null;
      evaluation = false;
    }
  }
}
