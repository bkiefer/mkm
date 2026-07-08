package de.dfki.mlt.drz.mkm;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dfki.lt.hfc.WrongFormatException;
import de.dfki.mlt.mqtt.MqttHandler;

public class TestPipeline {
  private static final Logger logger = LoggerFactory.getLogger(TestPipeline.class);

  List<String[]> msgs = new ArrayList<>();
  List<String[]> inbound = new ArrayList<>();
  boolean conditionMet;
  private CountDownLatch conditionLatch;

  void read_mqtt_msgs(Path f) throws IOException {
    try (BufferedReader in = Files.newBufferedReader(f)) {
      String line = null;
      while ((line = in.readLine()) != null) {
        String[] msg = line.split("\t");
        msgs.add(msg);
      }
    }
  }

  int good = 0, bad = 0;

  private class Checker implements Predicate<byte[]> {
    String topic;

    public Checker(String t) {
      topic = t;
    }

    @Override
    public boolean test(byte[] payload) {
      String[] nextmsg = msgs.removeFirst();
      // check it's the right topic
      if (conditionMet = topic.equals(nextmsg[1])) {
        String in = MqttHandler.bytesToString(payload);
        // check it is the right message
        if (!(conditionMet = nextmsg[2].equals(in))) {
          logger.error("Wrong msg from MKM:\n{} instead of \n{}", in, nextmsg[2]);
        }
      } else {
        logger.error("Wrong topic {} instead of {}", nextmsg[1], topic);
      }
      conditionLatch.countDown();
      return true;
    }
  }

  private boolean isInbound(String[] msg) {
    return msg[1].contains("whisperasr/asrresult");
  }

  public boolean test() throws IOException, MqttException, WrongFormatException,
  InterruptedException {
    read_mqtt_msgs(Path.of("src/test/resources/input.mqtt"));

    MqttHandler fakeAsr = new MqttHandler(new HashMap<>());
    String[] topics = { "whisperasr/speakeridentification", "mkm/result" };
    for (String t: topics) {
      fakeAsr.register(t, this.new Checker(t));
    }

    String args[] = { };
    Main.readConfig("src/test/resources/pipeline.yml");
    Main.main(args);

    while (! msgs.isEmpty()) {
      String[] msg = msgs.getFirst();
      if (isInbound(msg)) {
        //final int oldlen = outbound.size();
        fakeAsr.sendMessage(msg[1], msg[2]);
        msgs.removeFirst();
      } else {
        conditionMet = false;
        conditionLatch = new CountDownLatch(1);
        boolean inTime = conditionLatch.await(3, TimeUnit.SECONDS);
        if (! inTime) {
          conditionMet = false;
        }
        if (conditionMet) {
          ++good; //logger.debug("GOOD!!!! {} !!!!", good);
        } else {
          ++bad;
        }
        //Thread.sleep(500000);
      }
    }
    fakeAsr.sendMessage("mkm/control", "exit");
    fakeAsr.disconnect();
    return (msgs.isEmpty() && bad == 0 && good == 9);
  }

  public static void main(String[] args) throws WrongFormatException,
  IOException, MqttException, InterruptedException {
    TestPipeline tp = new TestPipeline();
    if (! tp.test()) {
      logger.error("Failure!");
      System.exit(1);
    } else {
      logger.info("SUCCESS!");
    }
  }
}

