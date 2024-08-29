package de.dfki.drz.mkm;

import static de.dfki.drz.mkm.Constants.INSTANCE_NS_SHORT;
import static de.dfki.mlt.rudimant.common.Configs.CFG_ONTOLOGY_FILE;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Map;

import de.dfki.lt.hfc.WrongFormatException;
import de.dfki.lt.hfc.db.HfcDbHandler;
import de.dfki.lt.hfc.db.rdfProxy.DbClient;
import de.dfki.lt.hfc.db.rdfProxy.Rdf;
import de.dfki.lt.hfc.db.rdfProxy.RdfProxy;
import de.dfki.mlt.rudimant.agent.Agent;
import de.dfki.mlt.rudimant.agent.Behaviour;
import de.dfki.mlt.rudimant.agent.nlp.DialogueAct;

public abstract class KnowledgeManager extends Agent {

  Rdf user;

  private DbClient handler;

  boolean evaluation = false;
  private Writer w = null;

  HfcUtils hu;

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

  protected void printEval(String id, String speaker, String addressee, String intent, String text) {
    try {
      w.append(id + "," + na(speaker) + "," + na(addressee) + ","
          + intent + ", \"" + text + '"');
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
