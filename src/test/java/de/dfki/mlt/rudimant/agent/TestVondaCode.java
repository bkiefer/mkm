package de.dfki.mlt.rudimant.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;

import de.dfki.lt.hfc.WrongFormatException;
import de.dfki.lt.tr.dialogue.cplan.DagNode;
import de.dfki.mlt.drz.mkm.MissionKnowledge;
import de.dfki.mlt.rudimant.agent.nlp.DialogueAct;

public class TestVondaCode {
  @Test
  public void test() throws WrongFormatException, IOException {
    MissionKnowledge mk = new MissionKnowledge();
    Map<String, String> cfg = Map.of(
        "ontologyFile", "src/main/resources/ontology/mkm.yml"
        );
    mk.init(new File("."), "de_DE", cfg);

    DialogueAct da =
        new DialogueAct(
            "Request",
            "Communication",
            "id",
            "77",
            "text",
            "Das ist ein Text",
            "fromTime",
            "7777777",
            "toTime",
            "8888888",
            "sender",
            "<mkm:GF>",
            "addressee",
            "<mkm:ZF>");
    DagNode dag = da.getDag();
    // massage einheit and auftrag into DAG list ...
    DagNode li = new DagNode(); li.setNominal();
    li.addEdge(DagNode.getFeatureId("first"),
        new DagNode(DagNode.PROP_FEAT_ID, new DagNode("Ihre Einheit")));
    dag.addEdge(DagNode.getFeatureId("einheit"), li);
    li = new DagNode(); li.setNominal();
    li.addEdge(DagNode.getFeatureId("first"),
        new DagNode(DagNode.PROP_FEAT_ID, new DagNode("zur Rettung verletzter Personen")));
    dag.addEdge(DagNode.getFeatureId("auftrag"), li);
    Map res = mk.makeFusion(da, da.getValue("sender"), null);
    System.out.println(res);
    assertEquals(res.get("id"), "77");
    assertTrue(res.get("einheit") instanceof List);
    assertEquals(((List)res.get("einheit")).get(0), "Ihre Einheit");
  }
}
