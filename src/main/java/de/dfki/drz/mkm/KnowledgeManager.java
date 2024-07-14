package de.dfki.drz.mkm;

import static de.dfki.mlt.rudimant.common.Configs.*;
import static de.dfki.drz.mkm.Constants.*;

import java.io.File;
import java.io.IOException;
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
  private HfcDbHandler server;

  HfcUtils hu;

  private RdfProxy startClient(File configDir, Map<String, Object> configs)
      throws IOException, WrongFormatException {
    String ontoFileName = (String) configs.get(CFG_ONTOLOGY_FILE);
    if (ontoFileName == null) {
      throw new IOException("Ontology file is missing.");
    }
    server = new HfcDbHandler(new File(configDir, ontoFileName).getPath());
    handler = new HfcDbHandler(ontoFileName);

    RdfProxy proxy = new RdfProxy(handler);
    handler.registerStreamingClient(proxy);
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
    if (server != null) server.shutdown();
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
}
