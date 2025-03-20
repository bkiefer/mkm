package de.dfki.drz.mkm;

import static de.dfki.drz.mkm.Constants.*;

import java.util.List;

import de.dfki.lt.hfc.db.rdfProxy.Rdf;
import de.dfki.lt.hfc.db.rdfProxy.RdfProxy;

public class HfcUtils {
  private RdfProxy proxy;

  public HfcUtils(RdfProxy p) {
    proxy = p;
  }

  public Rdf resolveAgentFromToken(String callsign) {
    List<Object> agents = proxy.query(
        "select distinct ?a where ?a <drz:hasToken> \"{}\" ?_ & ?a <rdf:type> <drz:Einsatzkraft> ?_",
        callsign);
    return agents.isEmpty() ? null : (Rdf)agents.get(0);
  }

  public Rdf resolveAgent(String callsign) {
    List<Object> agents = proxy.query(
        "select distinct ?a where ?a <drz:hasCallsign> \"{}\" ?_ & ?a <rdf:type> <drz:Einsatzkraft> ?_",
        callsign);
    return agents.isEmpty() ? null : (Rdf)agents.get(0);
  }

  public String resolveSpeaker(String callsign) {
    if (callsign.charAt(0) == '<' || callsign.charAt(0) == '#') {
      // it's already a URL
      return callsign;
    }
    // check if there is one in the database
    // TODO: maybe we need something more clever based on fuzzy matching
    Rdf speaker = resolveAgent(callsign);
    if (speaker == null) {
      speaker = proxy.getRdfClass(AGENT_URI).getNewInstance(INSTANCE_NS_SHORT);
      speaker.setValue("<drz:hasCallsign>", callsign);
    }
    return speaker.getURI();
  }

  public void addCallsign(String uri, String callsign) {
    proxy.getRdf(uri).setValue("<drz:hasCallsign>", callsign);
  }

  /*
  public Rdf findUserByName(String firstname) {
    List<Object> results = proxy.query(
        "select ?u where ?u <rdf:type> <vprof:User> ?_ & ?u <vprof:name> \"{}\" ?_",
        firstname);
    if (results.isEmpty()) return null;
    return (Rdf)results.get(0);
  }

  public Rdf findUserById(String id) {
    List<Object> results = proxy.query(
        "select ?u where ?u <rdf:type> <vprof:User> ?_ & ?u <vprof:userId> \"{}\" ?_",
        id);
    if (results.isEmpty()) return null;
    return (Rdf)results.get(0);
  }

  public String getUserLanguage(String userId) {
    Rdf user = findUserById(userId);
    if (user == null) return null;
    return user.getString("<vprof:language>");
  }

  private String getLastViewedChapter(String sessionId) {
    // get last viewed lecture and chapter in session
    List<Object> lectures = proxy.query(
        "select ?a where ?u <vprof:sessionId> \"{}\" ?_ & ?u <vprof:hasWatched> ?a ?_ & ?a <vprof:completed> \"false\" ?_",
        sessionId);
    if (lectures.isEmpty()) return null;
    Rdf lastLecture = (Rdf)lectures.get(0);
    List<Object> lastChapter = proxy.query(
        "select ?a where ?u <vprof:teachingUnitId> \"{}\" ?_ & ?u <vprof:lastChapter> ?a ?_",
        lastLecture.getString("<vprof:teachingUnitId>"));
    Rdf chapter = (Rdf)lastChapter.get(0);
    //return concatenation of lecture and chapter title
    return (lastLecture.getString("<vprof:title>") + ", " + chapter.getString("<vprof:title>"));
  }

  /** This is assuming that we're in the current session, which is in the db
   * already
   * @param user_uri
   * @return
   *
  public Rdf getLastSession(String user_uri) {
	 // get sessions, reverse sorted by time stamp
	 List<Object> sessions = proxy.query(
	     "select ?a ?t where {} <vprof:sessions> ?a ?_ "
	     + "& ?a <vprof:fromTime> ?t ?_ aggregate ?res = SortR ?a ?t",
	     user_uri);
	 if (sessions.size() <= 1) return null;
	 Rdf lastSession = (Rdf)sessions.get(1);
	 return lastSession;
  }

  /** Get last lecture that actually has a lastChapter *
  public Rdf getLastLecture(String user_uri) {
    // get sessions, reverse sorted by time stamp
    List<Object> wlectures = proxy.query(
        "select ?chap ?wlect ?t where {} <vprof:sessions> ?sess ?_ "
        + "& ?sess <vprof:hasWatched> ?wlect ?_ "
        + "& ?wlect <vprof:lastChapter> ?chap ?_ "
        + "& ?wlect <vprof:fromTime> ?t ?_ aggregate ?res = SortR ?wlect ?t",
        user_uri);
    if (wlectures.isEmpty()) return null;
    Rdf lastLecture = (Rdf)wlectures.get(0);
    return lastLecture;
  }

  public Rdf findLectureById(String lectureId) {
    //lectureId = lectureId.toLowerCase();
    List<Object> results = proxy.query(
        "select ?u where ?u <rdf:type> <vprof:Lecture> ?_ & ?u <vprof:teachingUnitId> \"{}\" ?_",
        lectureId);
    if (results.isEmpty()) return null;
    return (Rdf)results.get(0);
  }

  public int countChapters(String lecture_uri) {
    List<Object> results = proxy.query(
        "select distinct ?a where {} <vprof:contains> ?a ?_ & ?a <rdf:type> <vprof:Chapter> ?_",
        lecture_uri);
    return results.size();
  }

  public String listLectures() {
    List<List<Object>> rows = proxy.queryTable(
        "select distinct ?i ?u where ?a <vprof:title> ?u ?_"
            + " & ?a <rdf:type> <vprof:Lecture> ?_"
            + " & ?a <vprof:teachingUnitId> ?i ?_");
    String result = "{";
    if (! rows.isEmpty()) {
      List<Object> row = rows.get(0);
      result += "\"" + row.get(0) + "\" : \"" + row.get(1) + "\"";
      rows = rows.subList(1, rows.size());
    }

    for (List<Object> row : rows) {
      result = result + " ," + "\"" + row.get(0) + "\" : \"" + row.get(1) + "\"";
    }
    result += "}";
    return result;

    /*
	    if (results.isEmpty()) return null;
	    int count = 1;
	    String curr_item;
	    String ret = "\n";
	    for (Object lecture_name : results) {
	      curr_item = lecture_name.toString();
	      ret += String.valueOf(count) + ". " + curr_item + "\n";
	      ++count;
	    }
	    return ret;
	    //return (Rdf)results.get(0);
	     *
  }

  public String listUsers() {
    List<List<Object>> rows = proxy.queryTable(
        "select distinct ?id ?name where ?u <rdf:type> <vprof:User> ?_"
            + " & ?u <vprof:userId> ?id ?_"
            + " & ?u <vprof:name> ?name ?_");
    String result = "{";
    if (! rows.isEmpty()) {
      List<Object> row = rows.get(0);
      result += "\"" + row.get(0) + "\" : \"" + row.get(1) + "\"";
      rows = rows.subList(1, rows.size());
    }

    for (List<Object> row : rows) {
      result = result + " ," + "\"" + row.get(0) + "\" : \"" + row.get(1) + "\"";
    }
    result += "}";
    return result;
  }*/

}
