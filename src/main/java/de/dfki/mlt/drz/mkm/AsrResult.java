package de.dfki.mlt.drz.mkm;

import java.util.List;
import java.util.Map;

/*{
  "info": {
    "language": "de",
    "language_probability": 1,
    "duration": 1.248,
    "duration_after_vad": 1.248,
    "transcription_options": {
      "beam_size": 5,
      "best_of": 5,
      "patience": 1,
      "length_penalty": 1,
      "repetition_penalty": 1,
      "no_repeat_ngram_size": 0,
      "log_prob_threshold": -1,
      "no_speech_threshold": 0.6,
      "compression_ratio_threshold": 2.4,
      "condition_on_previous_text": true,
      "prompt_reset_on_temperature": 0.5,
      "temperatures": [
        0,
        0.2,
        0.4,
        0.6,
        0.8,
        1
      ],
      "suppress_blank": true,
      "suppress_tokens": [
        -1
      ],
      "without_timestamps": false,
      "max_initial_timestamp": 1,
      "word_timestamps": false,
      "prepend_punctuations": "\"'“¿([{-",
      "append_punctuations": "\"'.。,，!！?？:：”)]}、",
      "clip_timestamps": "0"
    }
  },
  "segments": [
    {
      "id": 1,
      "seek": 124,
      "start": 0,
      "end": 1.24,
      "text": "Hallo Computer!",
      "tokens": [
        50365,
        39,
        37104,
        22289,
        0
      ],
      "temperature": 0,
      "avg_logprob": -0.9617819388707479,
      "compression_ratio": 0.6521739130434783,
      "no_speech_prob": 0.008289361372590065
    }
  ]
}*/
class Segment {
  public int id;
  public float seek;
  public float start;
  public float end;
  public String text;
  public int[] tokens;
  public double temperature;
  public double avg_logprob;
  public double compression_ratio;
  public double no_speech_prob;
}

/** This is the internal representation of a whisper output compatible result */
public class AsrResult {
  public Map<String, Object> info;
  public List<Segment> segments;
  public long start, end;

  public Integer embedid;   // id of embedding (optional)
  public Double confidence; // confidence of embedding (optional)
  public String speaker;    // speaker id (optional)

  public String getText() {
    StringBuffer sb = new StringBuffer();
    for (Segment s : segments) {
      sb.append(s.text).append(' ');
    }
    return sb.toString().trim();
  }
}
