package de.dfki.mlt.drz.mkm;

import static de.dfki.mlt.mqtt.MqttHandler.bytesToString;

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

import de.dfki.lt.hfc.WrongFormatException;
import de.dfki.mlt.drz.mkm.util.Listener;
import de.dfki.mlt.mqtt.JsonMarshaller;
import de.dfki.mlt.mqtt.MqttHandler;
import de.dfki.mlt.rudimant.agent.Agent;
import de.dfki.mlt.rudimant.agent.Behaviour;
import de.dfki.mlt.rudimant.agent.CommunicationHub;
import de.dfki.mlt.rudimant.agent.Intention;
import de.dfki.mlt.rudimant.agent.nlp.DialogueAct;

public class MkmClient implements CommunicationHub {
  public static class IdString {
    public String id;
    public String text;
    public String speaker; // nullable, to evaluate perfect speaker oracle
    private IdString(String i, String t, String s) {
      id = i; text = t; speaker = s;
    }

    public static IdString getIdString(String str) {
      String[] parts = str.split("[|]");
      if (parts.length < 2) return null;
      String speaker = null;
      if (parts.length > 2) {
        speaker = parts[2];
      }
      return new IdString(parts[0], parts[1], speaker);
    }
  }

  private final static Logger logger = LoggerFactory.getLogger(MkmClient.class);

  private static final String ASR_TOPIC = "whisperasr/asrresult/de";
  private static final String SPEAKER_TOPIC = "whisperasr/speakeridentification";
  private static final String SLOTS_TOPIC = "mkm/result";
  private static final String STRING_TOPIC = "test/string";
  private static final String CONTROL_TOPIC = "mkm/control";

  /** How much time in milliseconds must pass between two behaviours, if
   *  no message came back that the previous behaviour was finished.
   */
  public static long MIN_TIME_BETWEEN_BEHAVIOURS = 10000;

  private MqttHandler client;
  private JsonMarshaller mapper;

  private Random r = new Random();

  private boolean isRunning = true;

  private File _configDir;
  private Map<String, Object> _configs;

  private MissionKnowledge _agent;

  private Deque<Object> inQueue = new ArrayDeque<>();
  private Deque<Object> itemsToSend = new ArrayDeque<>();

  private Deque<Object> pendingEvents = new ArrayDeque<>();

  private List<Listener<Behaviour>> _listeners = new ArrayList<>();

  /** While this method is executed, no MQTT messages should be processed. This
   * could be done using synchronized, but it would be better and more efficient
   * to temporarily disconnect from the broker.
   *
   * @param userId the user id for which a new agent shall be created
   * @throws IOException
   * @throws WrongFormatException
   */
  private void initAgent() throws WrongFormatException, IOException {
    _agent = new MissionKnowledge();
    String language = "de_DE";
    logger.debug("Initialising agent with language {}", language);
    _agent.init(_configDir, language, _configs);
    // needs to be done after grammar loading, but this is the default!
    //Interpreter.NO_RESULT = new DialogueAct("OutOfDomain(top)");

    _agent.logAllRules();
    _agent.setCommunicationHub(this);
    _agent.newData();
  }

  private void sendAsr(AsrResult asr) {
    if (asr.id == null) {
      asr.id = asr.source + asr.start;
    }
    sendEvent(_agent.digestASR(asr));
  }

  private boolean receiveAsr(byte[] b) {
    Optional<AsrResult> asr;
    (asr = mapper.unmarshal(b, AsrResult.class)).ifPresent(this::sendAsr);
    return ! asr.isEmpty();
  }

  private boolean receiveString(byte[] b) {
    String str = bytesToString(b);
    if (! str.isEmpty()) {
      int bar = str.indexOf('|');
      if (bar < 0) {
        sendEvent(_agent.digestStringForTesting(str));
      } else {
        IdString ids = IdString.getIdString(str);
        if (ids != null) {
          sendEvent(_agent.digestIdString(ids));
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

  /** Send speaker info to the speaker indentification module */
  public void addSpeaker(Speaker speaker) {
    sendToTopic(SPEAKER_TOPIC,
        String.format("{ \"id\": \"%s\", \"speaker\": \"%s\" }",
                      speaker.id, speaker.speaker));
  }

  public void sendCombined(String msg) {
    sendToTopic(SLOTS_TOPIC, msg);
  }

  public void sendToTopic(String topic, String msg) {
    client.sendMessage(topic, msg);
  }

  // depends on the concrete Event class
  private void onEvent(Object evt) {
    if (evt instanceof Intention) {
      logger.info("Incoming Intention: {}", evt);
      _agent.executeProposal((Intention)evt);
    } else if (evt instanceof DialogueAct) {
      logger.info("Incoming DialogueAct: {}", evt);
      _agent.addLastDA((DialogueAct)evt);
      _agent.newData();
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
      if (! inQueue.isEmpty() && (_agent.lastDA() == null
          || _agent.lastDA().getDialogueActType() != "Bottom")) {
        Object event = inQueue.pollFirst();
        onEvent(event);
        emptyRun = false;
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
    try {
        client.disconnect();
    } catch (MqttException e) {
      logger.error("Error disconnecting from MQTT: {}", e.getMessage());
    }
    logger.info("Exiting");
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
    isRunning = false;
  }
}
