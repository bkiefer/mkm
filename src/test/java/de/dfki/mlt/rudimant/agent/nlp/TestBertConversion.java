package de.dfki.mlt.rudimant.agent.nlp;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import de.dfki.lt.tr.dialogue.cplan.DagNode;
import de.dfki.mlt.drz.mkm.KnowledgeManager;
import de.dfki.mlt.drz.mkm.nlu.BertIntentSlotInterpreter;
import de.dfki.mlt.drz.mkm.nlu.BertSlotInterpreter;

public class TestBertConversion {

  private static Process p = null;
  /*
  @BeforeClass
  public static void startServer() throws IOException {
    ProcessBuilder pb = new ProcessBuilder(
        "modules/drz_intentslot/run_server.sh")
        .redirectErrorStream(true);
    p = pb.start();
    String line;
    if (! p.isAlive()) {
      BufferedReader r = p.errorReader();
      while ((line = r.readLine()) != null && ! line.isBlank()) {
        System.out.println(line);
      }
      throw new RuntimeException("Does not start");
    }
    BufferedReader r = p.inputReader();
    while (! (line = r.readLine()).contains("Serving on http:")) {
      System.out.println(line);
    }
  }
  
  @AfterClass
  public static void stopServer() throws InterruptedException {
    if (p != null) {
      p.destroyForcibly();
      while (p.isAlive()) {
        Thread.sleep(500);
      }
      p = null;
    }
  }
  */
  
  @Test
  @Ignore
  public void test() {
    Map<String, Object> config = new HashMap<>();
    config.put("port", 5050);
    config.put("converter", "src/main/resources/cplanner/adapterbertconv");
    BertIntentSlotInterpreter i = new BertIntentSlotInterpreter();
    i.init(new File("."), "de_DE", config);
  }

  @Test
  public void testConvert() {
    Map<String, Object> config = new HashMap<>();
    config.put("port", 5050);
    config.put("converter", "src/main/resources/cplanner/adapterbertconv");
    BertIntentSlotInterpreter i = new BertIntentSlotInterpreter();
    i.init(new File("."), "de_DE", config);
    String jsonstring = "{\"dialogue_act\": \"Einsatzbefehl\", "
        + "\"text\": \"Wassertrupp mit dem Rollschlauch zur Brandbek\\u00e4mpfung vor\", "
        + "\"phrases\": {"
        + "\"einheit\": [\"Wassertrupp\"], "
        + "\"auftrag\": [\"mit dem Rollschlauch zur Brandbek\\u00e4mpfung vor\"], "
        + "\"mittel\": [\"mit dem Rollschlauch\", \"und dem C-Rohr\"]}}";
    JSONObject jo = new JSONObject(jsonstring);
    DialogueAct da = i.convert(jo);
    List<String> mittel = KnowledgeManager.daList(
        da.getDag().getValue(DagNode.getFeatureId("mittel")));
    String[] exp = { "mit dem Rollschlauch", "und dem C-Rohr" };
    assertEquals(Arrays.asList(exp), mittel);
  }
  

  /*
  @Test
  public void testServerAndConvertSlotOnly() {
    Map<String, Object> config = new HashMap<>();
    config.put("port", 5050);
    config.put("converter", "src/main/resources/cplanner/adapterbertconv");
    BertSlotInterpreter i = new BertSlotInterpreter();
    i.init(new File("."), "de_DE", config);
    DialogueAct da = i.analyse(
        "Wassertrupp jetzt mit dem Rollschlauch zur Brandbekämpfung vorangehen!");
    assertEquals(5, da.getAllSlots().size());
    short first_id = DagNode.getFeatureId("first");
    DagNode d = 
        da.getDag().getValue(DagNode.getFeatureId("phrases"));
    assertEquals("mit dem Rollschlauch", 
        d.getValue(DagNode.getFeatureId("mittel"))
        .getValue(first_id).getValue(DagNode.PROP_FEAT_ID).getTypeName());
  }
  */
}
