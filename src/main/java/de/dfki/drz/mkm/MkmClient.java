package de.dfki.drz.mkm;

import java.io.File;
import java.io.IOException;
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
import de.dfki.lt.hfc.types.XsdDouble;
import de.dfki.lt.hfc.types.XsdFloat;
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

  private static final String CFG_ONTOLOGY_FILE = "ontologyFile";

  /** How much time in milliseconds must pass between two behaviours, if
   *  no message came back that the previous behaviour was finished.
   */
  public static long MIN_TIME_BETWEEN_BEHAVIOURS = 10000;


  public static float getDefault(Float d) {
    if (d == null) return 0.0f;
    return d;
  }

  public static double getDefault(Double d) {
    if (d == null) return 0.0;
    return d;
  }

  public static int getDefault(Integer d) {
    if (d == null) return 0;
    return d;
  }

  public static boolean getDefault(Boolean b) {
    if (b == null) return false;
    return b;
  }

  public static String float2xsd(Float f) {
    return new XsdFloat(getDefault(f)).toString();
  }

  public static String float2xsd(Double f) {
    return new XsdDouble(getDefault(f)).toString();
  }

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

  private RdfProxy startClient(File configDir, Map<String, Object> configs)
      throws IOException, WrongFormatException {
    String ontoFileName = (String) configs.get(CFG_ONTOLOGY_FILE);
    if (ontoFileName == null) {
      throw new IOException("Ontology file is missing.");
    }
    HfcDbHandler h = new HfcDbHandler(ontoFileName);
    handler = h;
    RdfProxy proxy = new RdfProxy(handler);

    return proxy;
  }

  /** While this method is executed, no MQTT messages should be processed. This
   * could be done using synchronized, but it would be better and more efficient
   * to temporarily disconnect from the broker.
   *
   * @param userId the user id for which a new agent shall be created
   */
  private void initAgent() {
    _agent = new MissionKnowledge();
    //_agent.hu = new HfcUtils(_proxy);
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

  private void initMqtt(Map<String, Object> configs) throws MqttException {
    mapper = new JsonMarshaller();
    client = new MqttHandler(configs);
    client.register(ASR_TOPIC, this::receiveAsr);
  }

  @SuppressWarnings("unchecked")
  public void init(File configDir, Map<String, Object> configs)
      throws IOException, WrongFormatException, MqttException {
    _configDir = configDir;
    _configs = configs;
    _proxy = startClient(_configDir, _configs);
    initAgent();
    initMqtt((Map<String, Object>)_configs.get("mqtt"));
  }

  public void registerBehaviourListener(Listener<Behaviour> listener) {
    _listeners.add(listener);
  }

  public void sendEvent(Object in) {
    inQueue.push(in);
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

  public DialogueAct analyse(String in) {
    return _agent.analyse(in);
  }

  // depends on the concrete Event class
  private void onEvent(Object evt) {
    if (evt instanceof Intention) {
      _agent.executeProposal((Intention)evt);
    } else if (evt instanceof DialogueAct) {
      // TODO: RESOLVE SENDER AND ADDRESSEE NAMES TO URIS, MAYBE OVERRIDE THE
      // ADDLASTDA METHOD
      _agent.addLastDA((DialogueAct)evt);
      _agent.newData();
    } else if (evt instanceof String) {
      DialogueAct da = _agent.analyse((String)evt);
      if (da == null) {
        da = Interpreter.NO_RESULT;
      }
      // TODO: RESOLVE SENDER AND ADDRESSEE NAMES TO URIS
      inQueue.add(da);
    } else if (evt instanceof AsrResult) {
      // TODO: check if we can filter out nonsense based on info from AsrResult
      String asr = ((AsrResult)evt).getText();
      logger.info("Incoming ASR message: {}", asr);
      inQueue.add(asr);
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
    // eventually connect to communication infrastructure
    // _communicationChannel.connect();
    Thread listenToClient = new Thread() {
      @Override
      public void run() { runReceiveSendCycle(); }
    };
    listenToClient.setName("ListenToEvents");
    listenToClient.setDaemon(true);
    listenToClient.start();
  }

  public void shutdown() {
    // eventually disconnect from communication infrastructure
    // _communicationChannel.disconnect();
    isRunning = false;
  }
}
