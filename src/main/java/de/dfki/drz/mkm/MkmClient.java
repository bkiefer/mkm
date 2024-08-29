package de.dfki.drz.mkm;

import static de.dfki.drz.mkm.util.Utils.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dfki.drz.mkm.util.Listener;
import de.dfki.lt.hfc.WrongFormatException;
import de.dfki.lt.hfc.db.HfcDbHandler;
import de.dfki.lt.hfc.db.rdfProxy.DbClient;
import de.dfki.lt.hfc.db.rdfProxy.RdfProxy;
import de.dfki.mlt.mqtt.JsonMarshaller;
import de.dfki.mlt.mqtt.MqttHandler;
import de.dfki.mlt.rudimant.agent.Agent;
import de.dfki.mlt.rudimant.agent.Behaviour;
import de.dfki.mlt.rudimant.agent.CommunicationHub;
import de.dfki.mlt.rudimant.agent.Intention;
import de.dfki.mlt.rudimant.agent.nlp.DialogueAct;
import de.dfki.mlt.rudimant.agent.nlp.Interpreter;

public class MkmClient implements CommunicationHub {

  private final static Logger logger = LoggerFactory.getLogger(MkmClient.class);

  private static final String ASR_TOPIC = "whisperasr/asrresult/de";
  private static final String STRING_TOPIC = "test/string";
  private static final String CONTROL_TOPIC = "mkm/control";

  private static final String CFG_ONTOLOGY_FILE = "ontologyFile";

  /** How much time in milliseconds must pass between two behaviours, if
   *  no message came back that the previous behaviour was finished.
   */
  public static long MIN_TIME_BETWEEN_BEHAVIOURS = 10000;

  private DbClient handler;

  private MqttHandler client;
  private JsonMarshaller mapper;

  private Random r = new Random();

  private boolean isRunning = true;

  private File _configDir;
  private Map<String, Object> _configs;
  private RdfProxy _proxy;
  private MissionKnowledge _agent;

  private Deque<Object> inQueue = new ArrayDeque<>();
  private Deque<Object> itemsToSend = new ArrayDeque<>();

  private Deque<Object> pendingEvents = new ArrayDeque<>();

  private List<Listener<Behaviour>> _listeners = new ArrayList<>();

  private void startHfcClient(File configDir, Map<String, Object> configs)
      throws IOException, WrongFormatException {
    String ontoFileName = (String) configs.get(CFG_ONTOLOGY_FILE);
    if (ontoFileName == null) {
      throw new IOException("Ontology file is missing.");
    }
    handler = new HfcDbHandler(ontoFileName);
    _proxy = new RdfProxy(handler);
  }

  /** While this method is executed, no MQTT messages should be processed. This
   * could be done using synchronized, but it would be better and more efficient
   * to temporarily disconnect from the broker.
   *
   * @param userId the user id for which a new agent shall be created
   */
  private void initAgent() {
    _agent = new MissionKnowledge();
    _agent.hu = new HfcUtils(_proxy);
    String language = "de_DE";
    logger.debug("Initialising agent with language {}", language);
    _agent.init(_configDir, language, _proxy, _configs, "volu");
    // needs to be done after grammar loading
    Interpreter.NO_RESULT = new DialogueAct("OutOfDomain(top)");

    _agent.logAllRules();
    _agent.setCommunicationHub(this);
    _agent.newData();
  }

  private boolean receiveAsr(byte[] b) {
    Optional<AsrResult> asr;
    (asr = mapper.unmarshal(b, AsrResult.class)).ifPresent(this::sendEvent);
    return ! asr.isEmpty();
  }

  private String bytesToString(byte[] b) {
    StringBuilder sb = new StringBuilder();
    int c;
    try (Reader r = new InputStreamReader(new ByteArrayInputStream(b),
        Charset.forName("UTF-8"))) {
      while ((c = r.read()) >= 0) {
        sb.append((char)c);
      }
    } catch (IOException ex) { // will not happen
    }
    return sb.toString();
  }
  
  private boolean receiveString(byte[] b) {
    String str = bytesToString(b);
    if (! str.isEmpty()) {
      int bar = str.indexOf('|');
      if (bar < 0) {
        sendEvent(str);
      } else {
        IdString ids = IdString.getIdString(str);
        if (ids != null) {
          sendEvent(ids);
        }
      }
    }
    return ! str.isEmpty();
  }

  private boolean receiveCommand(byte[] b) {
    String cmd = bytesToString(b);
    switch (cmd) {
    case "exit": shutdown(); break;
    default: logger.warn("Unknown command: {}", cmd); break;
    }
    return ! cmd.isEmpty();
  }
  
  private void initMqtt(Map<String, Object> configs) throws MqttException {
    mapper = new JsonMarshaller();
    client = new MqttHandler(configs);
    client.register(ASR_TOPIC, this::receiveAsr);
    client.register(STRING_TOPIC, this::receiveString);
    client.register(CONTROL_TOPIC, this::receiveCommand);
  }

  @SuppressWarnings("unchecked")
  public void init(File configDir, Map<String, Object> configs)
      throws IOException, WrongFormatException, MqttException {
    _configDir = configDir;
    _configs = configs;
    startHfcClient(_configDir, _configs);
    initAgent();
    initMqtt((Map<String, Object>)_configs.get("mqtt"));
  }

  public void registerBehaviourListener(Listener<Behaviour> listener) {
    _listeners.add(listener);
  }

  public Agent getAgent() { return _agent; }

  @Override
  public void sendBehaviour(Behaviour b) {
    for (Listener<Behaviour> l : _listeners) {
      l.listen(b);
    }
  }

  // select one of a set of intentions
  @Override
  public void sendIntentions(Set<String> intentions) {
    if (intentions.isEmpty()) return;
    // The following is a stub "statistical" component which randomly selects
    // one intention
    int rand = r.nextInt(intentions.size());
    String intention = null;
    Iterator<String> it = intentions.iterator();
    for(int i = 0; i <= rand; ++i) {
      intention = it.next();
    }
    inQueue.push(new Intention(intention, 0.0));
  }

  private void addWithMetaData(DialogueAct da, long start, long end) {
    if (da == null) {
      da = Interpreter.NO_RESULT;
    }
    // resolve sender and addressee names to uris, eventually creating new
    // Einsatzkraft/Agent instances
    if (da.hasSlot("sender")) {
      da.setValue("sender", _agent.hu.resolveSpeaker(da.getValue("sender").trim()));
    }
    if (da.hasSlot("addressee")) {
      da.setValue("addressee", _agent.hu.resolveSpeaker(da.getValue("addressee").trim()));
    }
    da.setValue("fromTime", num2xsd(start));
    da.setValue("toTime", num2xsd(end));
    _agent.addLastDA(da);
    _agent.newData();
  }

  // depends on the concrete Event class
  private void onEvent(Object evt) {
    if (evt instanceof Intention) {
      _agent.executeProposal((Intention)evt);
    } else if (evt instanceof DialogueAct) {
      _agent.addLastDA((DialogueAct)evt);
      _agent.newData();
    } else if (evt instanceof String) {
      logger.info("Incoming String message: {}", evt);
      DialogueAct da = _agent.analyse(((String)evt).trim());
      long now = System.currentTimeMillis();
      addWithMetaData(da, now, now);
      da.setValue("text", (String)evt);
      long now = System.currentTimeMillis();
      addWithMetaData(da, now, now);
    } else if (evt instanceof AsrResult) {
      AsrResult res = ((AsrResult)evt);
      // TODO: check if we can filter out nonsense based on info from AsrResult
      String asr = res.getText();
      logger.info("Incoming ASR message: {}", asr);
      DialogueAct da = _agent.analyse((String)evt);
      addWithMetaData(da, res.start, res.end);
    } else {
      logger.warn("Unknown incoming object: {}", evt);
    }
  }

  // Depends on the concrete Event class
  private void sendThis(Object e) {
    if (e instanceof Behaviour)
      sendBehaviour((Behaviour)e);
    else
      logger.warn("Unknown Object to send: {}", e);
  }

  private boolean isRunning() {
    return isRunning;
  }
  
  public void sendEvent(Object in) {
    inQueue.addLast(in);
  }

  private void runReceiveSendCycle() {
    while (isRunning()) {
      boolean emptyRun = true;
      while (! inQueue.isEmpty()) {
        Object event = inQueue.pollFirst();
        onEvent(event);
      }
      // if a proposal was executed, handle pending events now
      if (!_agent.waitForIntention()) {
        // handle any pending events
        while (!pendingEvents.isEmpty()) {
          onEvent(pendingEvents.removeLast());
        }
        _agent.processRules();
      }
      synchronized (itemsToSend) {
        Object c = itemsToSend.peekFirst();
        if (c != null && (c instanceof Behaviour)
            && _agent.waitForBehaviours((Behaviour)c)) {
          c = null;
        }
        if (c != null) {
          itemsToSend.removeFirst();
          logger.debug("<-- {}", c);
          sendThis(c);
          emptyRun = false;
        }
      }
      if (emptyRun) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException ex) {
          // shut down?
        }
      }
    }
    _agent.shutdown();
  }

  public void startListening() {
    // connect to communication infrastructure done in init()
    Thread listenToClient = new Thread() {
      @Override
      public void run() { runReceiveSendCycle(); }
    };
    listenToClient.setName("ListenToEvents");
    listenToClient.setDaemon(true);
    listenToClient.start();
  }

  public void shutdown() {
    try {
      _agent.shutdown();
      client.disconnect();
    } catch (MqttException e) {
      logger.error("Error disconnecting from MQTT: {}", e.getMessage());
    }
    isRunning = false;
  }
}
