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
import de.dfki.lt.hfc.db.rdfProxy.Rdf;
import de.dfki.mlt.mqtt.JsonMarshaller;
import de.dfki.mlt.mqtt.MqttHandler;
import de.dfki.mlt.rudimant.agent.Agent;
import de.dfki.mlt.rudimant.agent.Behaviour;
import de.dfki.mlt.rudimant.agent.CommunicationHub;
import de.dfki.mlt.rudimant.agent.Intention;
import de.dfki.mlt.rudimant.agent.nlp.DialogueAct;
import de.dfki.mlt.rudimant.agent.nlp.Interpreter;

public class MkmClient implements CommunicationHub {
  private static class IdString {
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

  private static double CONFIDENCE_THRESHOLD = 0.7;

  /** How much time in milliseconds must pass between two behaviours, if
   *  no message came back that the previous behaviour was finished.
   */
  public static long MIN_TIME_BETWEEN_BEHAVIOURS = 10000;

  private MqttHandler client;
  private JsonMarshaller mapper;

  private Random r = new Random();

  private boolean isRunning = true;
  private int _running_id = 0;
  private int _running_userId = 0;

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

  public void addSpeaker(Speaker speaker) {
    sendToTopic(SPEAKER_TOPIC,
        String.format("{ \"id\": %d, \"speaker\": \"%s\" }",
            speaker.id, speaker.speaker));
  }

  public void sendCombined(String msg) {
    sendToTopic(SLOTS_TOPIC, msg);
  }

  public void sendToTopic(String topic, String msg) {
    client.sendMessage(topic, msg);
  }

  private void addWithMetaData(DialogueAct da, long start, long end, Speaker speaker) {
    if (da == null) {
      da = Interpreter.NO_RESULT;
    }
    // do we have a result from audio speaker recognition?
    if (speaker != null) {
      if (! speaker.speaker.equals("Unknown")) {
        // it's a URI!
        if (da.hasSlot("sender")) {
          String nluSenderName = da.getValue("sender");
          Rdf nluSender = _agent.hu.resolveAgent(nluSenderName);
          // agent found for callsign in transcription ?
          if (nluSender != null) {
            // the URI computed from the NLU result
            String nluSenderUri = nluSender.getURI().trim();
            if (nluSenderUri == speaker.speaker) {
              // we agree, add supporting embedding
              addSpeaker(speaker);
            } else {
              // NLU and audio identify two different (known) people
              // How confident is the audio speaker recognition?
              if (speaker.confidence < CONFIDENCE_THRESHOLD) {
                // add new speaker evidence to audio speaker recognition
                speaker.speaker = nluSenderUri;
                addSpeaker(speaker);
              }
            }
          } else {
            // add callsign to the agent from audio identification (and NLU?)
            _agent.hu.addCallsign(speaker.speaker, nluSenderName);
          }
          da.setValue("sender", speaker.speaker);
        }
      } else {
        // audio identification is unsure or new speaker
        // check if we have something from transcription
        Rdf sender = da.hasSlot("sender")
            ? _agent.toRdf(_agent.hu.resolveSpeaker(da.getValue("sender")))
            // create a new Einsatzkraft with unique intermediate name
            : _agent.toRdf(_agent.hu.resolveSpeaker(
                String.format("Unknown%02d", ++_running_userId)));
        speaker.speaker = sender.getURI().trim();
        addSpeaker(speaker);
        da.setValue("sender", speaker.speaker);
     }
    }

    // resolve sender and addressee names to uris if necessary, eventually
    // creating new Einsatzkraft/Agent instances
    if (da.hasSlot("sender") &&
        ! (da.getValue("sender").charAt(0) == '<'
           || da.getValue("sender").charAt(0) == '#')) {
      da.setValue("sender",
          _agent.hu.resolveSpeaker(da.getValue("sender").trim()));
    }
    if (da.hasSlot("addressee")) {
      da.setValue("addressee",
          _agent.hu.resolveSpeaker(da.getValue("addressee").trim()));
    }
    da.setValue("fromTime", num2xsd(start));
    da.setValue("toTime", num2xsd(end));
    if (! da.hasSlot("id")) {
      da.setValue("id", num2xsd(_running_id++));
    }
    inQueue.push(da);
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
    } else if (evt instanceof String) {
      logger.info("Incoming String message: {}", evt);
      String text = ((String)evt).trim();
      DialogueAct da = _agent.analyse(text);
      long now = System.currentTimeMillis();
      da.setValue("text", text);
      addWithMetaData(da, now, now, null);
    } else if (evt instanceof IdString) {
      IdString is = (IdString)evt;
      _agent.startEvaluation();
      logger.info("Incoming IdString message: {}|{}|{}", is.id, is.text, is.speaker);
      String text = (is.text).trim();
      DialogueAct da = _agent.analyse(text);
      long now = System.currentTimeMillis();
      if (!da.hasSlot("sender") && is.speaker != null && !is.speaker.isBlank()) {
        // we have to change this since we only send the token, not the callsign
        da.setValue("sender", _agent.hu.resolveSpeaker(is.speaker));
      }
      da.setValue("text", text);
      da.setValue("id", is.id);
      addWithMetaData(da, now, now, null);
    } else if (evt instanceof AsrResult) {
      AsrResult res = ((AsrResult)evt);
      // TODO: check if we can filter out nonsense based on info from AsrResult
      String asr = res.getText();
      logger.info("Incoming ASR message: {}", asr);
      DialogueAct da = _agent.analyse(asr);
      // id is speaker id, this comes from speaker identification
      if (res.speaker != null) {
        // fill the lastSpeaker
        _agent.lastSpeaker = new Speaker(res.embedid, res.speaker,
            res.confidence);
      } else {
        _agent.lastSpeaker = null;
      }
      if (!da.hasSlot("text")) {
        da.setValue("text", asr);
      }
      da.setValue("id", num2xsd(_running_id++));
      addWithMetaData(da, res.start, res.end, _agent.lastSpeaker);
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
