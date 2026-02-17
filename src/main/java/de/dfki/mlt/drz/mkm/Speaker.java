package de.dfki.mlt.drz.mkm;

public class Speaker {
  public Speaker(String uniqid, String s, double c) {
    id = uniqid;
    speaker = s;
    confidence = c;
  }

  public String id;
  public String speaker;
  public double confidence;
}
