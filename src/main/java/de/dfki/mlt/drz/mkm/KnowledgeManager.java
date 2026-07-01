package de.dfki.mlt.drz.mkm;

import static de.dfki.lt.tr.dialogue.cplan.DagNode.PROP_FEAT_ID;
import static de.dfki.mlt.drz.mkm.Constants.INSTANCE_NS_SHORT;
import static de.dfki.mlt.rudimant.common.Configs.CFG_ONTOLOGY_FILE;
import static de.dfki.mlt.drz.mkm.util.Utils.num2xsd;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONWriter;

import de.dfki.lt.hfc.WrongFormatException;
import de.dfki.lt.hfc.db.HfcDbHandler;
import de.dfki.lt.hfc.db.rdfProxy.DbClient;
import de.dfki.lt.hfc.db.rdfProxy.Rdf;
import de.dfki.lt.hfc.db.rdfProxy.RdfProxy;
import de.dfki.lt.hfc.types.XsdAnySimpleType;
import de.dfki.lt.tr.dialogue.cplan.DagNode;
import de.dfki.mlt.rudimant.agent.Agent;
import de.dfki.mlt.rudimant.agent.Behaviour;
import de.dfki.mlt.rudimant.agent.nlp.DialogueAct;
import de.dfki.mlt.rudimant.agent.nlp.Interpreter;

public abstract class KnowledgeManager extends Agent {

  protected Rdf user;

  private DbClient handler;

  protected boolean evaluation = false;
  private Writer w = null;

  private static double CONFIDENCE_THRESHOLD = 0.7;
  private int _running_id = 0;
  private int _running_userId = 0;

  HfcUtils hu;

  // If set, it stores the last speaker that was identified using the audio
  // speaker identification
  Speaker lastIdentifiedSpeaker = null;

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

  private static String getAtom(DagNode dag) {
    return dag.getValue(PROP_FEAT_ID).getTypeName();
  }

  public static List<String> daList(DagNode dag) {
    List<String> result = new ArrayList<>();
    while (dag != null && dag.getEdge(DagNode.getFeatureId("first")) != null) {
      String val = getAtom(dag.getValue(DagNode.getFeatureId("first")));
      result.add(val);
      dag = dag.getValue(DagNode.getFeatureId("rest"));
    }
    return result;
  }

  protected String na(String in) { return in == null ? "NA" : in; }

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

  @SuppressWarnings({ "rawtypes" })
  void sendFusion(Map slots) {
    ((MkmClient)_hub).sendCombined(JSONWriter.valueToString(slots));
  }

  private DialogueAct addWithMetaData(DialogueAct da, long start, long end) {
    MkmClient hub = (MkmClient)_hub;
    Speaker speaker = lastIdentifiedSpeaker;
    if (da == null) {
      da = Interpreter.NO_RESULT;
    }
    // do we have a result from audio speaker recognition?
    if (speaker != null) {
      if (! speaker.speaker.equals("Unknown")) {
        // it's a URI!
        if (da.hasSlot("sender")) {
          String nluSenderName = da.getValue("sender");
          Rdf nluSender = hu.resolveAgent(nluSenderName);
          // agent found for callsign in transcription ?
          if (nluSender != null) {
            // the URI computed from the NLU result
            String nluSenderUri = nluSender.getURI().trim();
            if (nluSenderUri == speaker.speaker) {
              // we agree, add supporting embedding
              hub.addSpeaker(speaker);
            } else {
              // NLU and audio identify two different (known) people
              // How confident is the audio speaker recognition?
              if (speaker.confidence < CONFIDENCE_THRESHOLD) {
                // add new speaker evidence to audio speaker recognition
                speaker.speaker = nluSenderUri;
                hub.addSpeaker(speaker);
              }
            }
          } else {
            // add callsign to the agent from audio identification (and NLU?)
            hu.addCallsign(speaker.speaker, nluSenderName);
          }
        }
      } else {
        // audio identification is unsure or new speaker
        // check if we have something from transcription
        Rdf sender = da.hasSlot("sender")
            ? toRdf(hu.resolveSpeaker(da.getValue("sender")))
            // create a new Einsatzkraft with unique intermediate name
            : toRdf(hu.resolveSpeaker(
                String.format("Unknown%02d", ++_running_userId)));
        speaker.speaker = sender.getURI().trim();
        hub.addSpeaker(speaker);
      }
      da.setValue("sender", speaker.speaker);
    }

    // resolve sender and addressee names to uris if necessary, eventually
    // creating new Einsatzkraft/Agent instances (first never applies if
    // speaker != null, since it's an URI then).
    if (da.hasSlot("sender") &&
        ! (da.getValue("sender").charAt(0) == '<'
           || da.getValue("sender").charAt(0) == '#')) {
      da.setValue("sender",
          hu.resolveSpeaker(da.getValue("sender").trim()));
    }
    if (da.hasSlot("addressee")) {
      da.setValue("addressee",
          hu.resolveSpeaker(da.getValue("addressee").trim()));
    }
    da.setValue("fromTime", num2xsd(start));
    da.setValue("toTime", num2xsd(end));
    if (! da.hasSlot("id")) {
      da.setValue("id", num2xsd(_running_id++));
    }
    return da;
  }

  DialogueAct digestASR(AsrResult res) {
    lastIdentifiedSpeaker = null;
    // TODO: check if we can filter out nonsense based on info from AsrResult
    String asr = res.getText();
    logger.info("Incoming ASR message: {}", asr);
    DialogueAct da = analyse(asr);
    // res.id is speaker id, res.speaker comes from speaker identification
    if (res.speaker != null) {
      // fill the lastSpeaker
      lastIdentifiedSpeaker = new Speaker(res.id, res.speaker, res.confidence);
    }
    if (!da.hasSlot("text")) {
      da.setValue("text", asr);
    }
    da.setValue("id", res.id);
    // da can have slots speaker and addresse, too, but these need to be
    // resolved
    return addWithMetaData(da, res.start, res.end);
  }

  DialogueAct digestStringForTesting(String text) {
    lastIdentifiedSpeaker = null;
    logger.info("Incoming String message: {}", text);
    DialogueAct da = analyse(text);
    long now = System.currentTimeMillis();
    da.setValue("text", text);
    return addWithMetaData(da, now, now);
  }

  DialogueAct digestIdString(MkmClient.IdString is) {
    lastIdentifiedSpeaker = null;
    startEvaluation();
    logger.info("Incoming IdString message: {}|{}|{}", is.id, is.text, is.speaker);
    String text = (is.text).trim();
    DialogueAct da = analyse(text);
    long now = System.currentTimeMillis();
    // is.speaker is the fake equivalent of audio speaker identification
    if (!da.hasSlot("sender") && is.speaker != null && !is.speaker.isBlank()) {
      // we have to change this since we only send the token, not the callsign
      da.setValue("sender", hu.resolveSpeaker(is.speaker));
    }
    da.setValue("text", text);
    da.setValue("id", is.id);
    return addWithMetaData(da, now, now);
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
