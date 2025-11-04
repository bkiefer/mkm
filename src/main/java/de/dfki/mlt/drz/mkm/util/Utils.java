package de.dfki.mlt.drz.mkm.util;

import de.dfki.lt.hfc.types.XsdDouble;
import de.dfki.lt.hfc.types.XsdFloat;
import de.dfki.lt.hfc.types.XsdInt;
import de.dfki.lt.hfc.types.XsdLong;

public class Utils {
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

  public static long getDefault(Long d) {
    if (d == null) return 0L;
    return d;
  }

  public static boolean getDefault(Boolean b) {
    if (b == null) return false;
    return b;
  }

  public static String numxsd(Float f) {
    return new XsdFloat(getDefault(f)).toString();
  }

  public static String num2xsd(Double f) {
    return new XsdDouble(getDefault(f)).toString();
  }

  public static String num2xsd(Integer f) {
    return new XsdInt(getDefault(f)).toString();
  }

  public static String num2xsd(Long f) {
    return new XsdLong(getDefault(f)).toString();
  }

}
