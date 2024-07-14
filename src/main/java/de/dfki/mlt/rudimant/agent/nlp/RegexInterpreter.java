package de.dfki.mlt.rudimant.agent.nlp;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexInterpreter extends Interpreter {

  // a map of regular expressions to dialogue acts
  public static final String CFG_REGEX_EXPRESSIONS = "expressions";
  // a list of files containing mappings like in the expressions section
  public static final String CFG_REGEX_FILES = "files";

  private Map<Pattern, String> patterns;

  @Override
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public boolean init(File configDir, String language, Map config) {
    boolean result = super.init(configDir, language, config);
    if (! result) return result;
    patterns = new HashMap<>();
    Map<String, String> conversions =
        (Map<String, String>)config.get(CFG_REGEX_EXPRESSIONS);
    for (Map.Entry<String,String> regex2res : conversions.entrySet()) {
      Pattern p = Pattern.compile(regex2res.getKey());
      patterns.put(p, regex2res.getValue());
    }
    return result;
  }

  @Override
  public DialogueAct analyse(String text) {
    for (Map.Entry<Pattern, String> regex2res : patterns.entrySet()) {
      Matcher m = regex2res.getKey().matcher(text);
      if (m.matches()) {
        String sb = new String(regex2res.getValue());
        for(int g = 1; g <= m.groupCount(); ++g) {
          sb = sb.replaceAll("\\{" + g + "\\}", m.group(g));
        }
        return new DialogueAct(sb);
      }
    }
    return null;
  }

}
