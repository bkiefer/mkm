package de.dfki.mlt.rudimant.agent.nlp;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.junit.Ignore;
import org.junit.Test;

import de.dfki.drz.mkm.KnowledgeManager;
import de.dfki.drz.mkm.nlu.AdapterBertInterpreter;
import de.dfki.lt.tr.dialogue.cplan.DagNode;

public class TestBertConversion {

  @Test
  @Ignore
  public void test() {
    Map<String, Object> config = new HashMap<>();
    config.put("port", 5050);
    config.put("converter", "src/main/resources/cplanner/adapterbertconv");
    AdapterBertInterpreter i = new AdapterBertInterpreter();
    i.init(new File("."), "de_DE", config);
  }

  @Test
  public void testConvert() {
    Map<String, Object> config = new HashMap<>();
    config.put("port", 5050);
    config.put("converter", "src/main/resources/cplanner/adapterbertconv");
    AdapterBertInterpreter i = new AdapterBertInterpreter();
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
        da.getDag("phrases").getValue(DagNode.getFeatureId("mittel")));
    String[] exp = { "mit dem Rollschlauch", "und dem C-Rohr" };
    assertEquals(Arrays.asList(exp), mittel);
  }
}
