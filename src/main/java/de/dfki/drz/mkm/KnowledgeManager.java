package de.dfki.drz.mkm;

import static de.dfki.drz.mkm.Constants.INSTANCE_NS_SHORT;
import static de.dfki.mlt.rudimant.common.Configs.CFG_ONTOLOGY_FILE;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.dfki.lt.hfc.WrongFormatException;
import de.dfki.lt.hfc.db.HfcDbHandler;
import de.dfki.lt.hfc.db.rdfProxy.DbClient;
import de.dfki.lt.hfc.db.rdfProxy.Rdf;
import de.dfki.lt.hfc.db.rdfProxy.RdfProxy;
import de.dfki.mlt.drz.eurocommand_api.model.MissionResourceRestApiContract;
import de.dfki.mlt.drz.fraunhofer_api.ApiException;
import de.dfki.mlt.drz.fraunhofer_api.api.DefaultApi;
import de.dfki.mlt.drz.fraunhofer_api.model.RadioMessage;
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

  private static final DefaultApi api = new DefaultApi();
  //private static final SimpleDateFormat sdf =
  //    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

  EuroCommandClient ECclient = new EuroCommandClient();
  UUID missionId;

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

  private void initIAISApi() {
    api.setCustomBaseUrl("https://eve.iais.fraunhofer.de/radio-transcription");
    api.getApiClient().setUsername("development");
    api.getApiClient().setPassword("LookMomNoVPN!");
  }

  private void initECApi() {
    try {
      List<MissionResourceRestApiContract> missionResources =
          ECclient.getMissionResources(missionId);
      if (!missionResources.isEmpty()) {
        missionId = missionResources.get(0).getMissionId();
      }
    } catch (de.dfki.mlt.drz.eurocommand_api.ApiException ex) {
      missionId = null;
    }
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
    this.evaluation = configs.containsKey("evaluation") &&
        (boolean)configs.get("evaluation");

    initIAISApi();
    initECApi();
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

  private String na(String in) { return in == null ? "NA" : in; }

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

      w.append(id + "," + na(speaker) + "," + na(addressee) + ","
          + daType + ", \"" + text + '"');
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

  private OffsetDateTime toODT(long l) {
    return new Date(l).toInstant().atZone(ZoneId.systemDefault())
      .toOffsetDateTime();
  }

  protected void sendMessageToIAIS(String sender, String receiver,
      String message, long fromTime, long toTime) {

    List<RadioMessage> radioMessages = new ArrayList<>();
    radioMessages.add(new RadioMessage()
        .sender(sender)
        .receiver(receiver)
        .message(message)
        .startTime(toODT(fromTime))
        .endTime(toODT(toTime)));
    try {
      api.addRadioMessagesAddMessagesPut(radioMessages);
      // if there is no exception, putting messages was successful
    } catch (ApiException e) {
      logger.error("Sending Message failed: {}", e.getMessage());
    }
  }

  protected void sendMessageToEC(String sender, String receiver,
      String message, long fromTime, long toTime) {
    try {
      ECclient.sendMessage(message, sender, receiver, missionId);
    } catch (de.dfki.mlt.drz.eurocommand_api.ApiException ex) {
      logger.error("EC sending: {}", ex);
    }
  }
}