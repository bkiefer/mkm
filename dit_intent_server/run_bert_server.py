#!/usr/bin/env python

import sys
import os
import flask
import torch
from pytorch_transformers import (BertForSequenceClassification, BertTokenizer)

app = flask.Flask(__name__)
app.config['TESTING'] = True
model = None
device = None
tokenizer = None

host = "0.0.0.0"
port = "8665"

mode = 'prev'
model_dir = "/data/models/dit_dia_server/"
label_list = ["Promise", "AcceptRequest", "Answer", "Confirm", "Agreement", "Retraction", "SetQuestion", "Disagreement",
              "Communicative Function", "Auto-negative", "TurnAssign", "Inform", "AcceptOffer",
              "InteractionStructuring",
              "FeedbackElicitation", "TurnAccept", "SelfError", "Suggestion", "CheckQuestion", "Offer",
              "ChoiceQuestion",
              "AcceptSuggestion", "Interaction Structuring", "DeclineOffer", "AddressOffer", "TurnTake",
              "SelfCorrection",
              "DeclineRequest", "Question", "Instruct", "Pausing", "PropositionalQuestion", "Disconfirm", "TurnRelease",
              "Allo-positive", "Thanking", "Auto-positive", "Stalling", "Opening", "AddressRequest",
              "Feedback Elicitation",
              "Request"]
max_seq_length = 128


class InputFeatures(object):
    def __init__(self, input_ids, input_mask, segment_ids, label_id):
        self.input_ids = input_ids
        self.input_mask = input_mask
        self.segment_ids = segment_ids
        self.label_id = label_id


@app.route("/predict", methods=["POST"])
def predict():
    # initialize the data dictionary that will be returned from the
    # view
    data = {"success": False, "request": flask.request.method,
            "old": flask.request.values.get("transcript_old"),
            "new": flask.request.values.get("transcript_new")}
    app.logger.info('Predicting: %s ', str(flask.request.values))
    if flask.request.method == "POST":
        if flask.request.values.get("transcript_old") and flask.request.values.get("transcript_new"):
            transcript_old = flask.request.values["transcript_old"]
            transcript_new = flask.request.values["transcript_new"]
            app.logger.debug("Transcript_old: %s Transcript_new: %s", transcript_old, transcript_new)
            # preprocess the image and prepare it for classification
            classify(transcript_old, transcript_new, data)
            data["success"] = True

    # return the data dictionary as a JSON response
    return flask.jsonify(data)


@app.route("/alive", methods=["GET"])
def alive():
    data = {"success": True}
    return flask.jsonify(data)


def _truncate_seq_pair(tokens_a, tokens_b, max_length):
    """Truncates a sequence pair in place to the maximum length."""

    # This is a simple heuristic which will always truncate the longer sequence
    # one token at a time. This makes more sense than truncating an equal percent
    # of tokens from each, since if one sequence is very short then each token
    # that's truncated likely contains more information than a longer sequence.
    while True:
        total_length = len(tokens_a) + len(tokens_b)
        if total_length <= max_length:
            break
        if len(tokens_a) > len(tokens_b):
            tokens_a.pop()
        else:
            tokens_b.pop()


def prepareData(transcript, transcipt_old):
    tokens_b = tokenizer.tokenize(transcript)
    tokens_a = tokenizer.tokenize(transcipt_old)
    _truncate_seq_pair(tokens_a, tokens_b, max_seq_length - 3)
    sent = 0
    tokens = ["[CLS]"]
    segment_ids = [sent]
    if tokens_a:
        tokens += tokens_a + ["[SEP]"]
        segment_ids = [sent] * len(tokens)
        sent += 1

    tokens += tokens_b + ["[SEP]"]
    segment_ids += [sent] * (len(tokens_b) + 1)

    input_ids = tokenizer.convert_tokens_to_ids(tokens)

    # The mask has 1 for real tokens and 0 for padding tokens. Only real
    # tokens are attended to.
    input_mask = [1] * len(input_ids)

    # Zero-pad up to the sequence length.
    padding = [0] * (max_seq_length - len(input_ids))
    input_ids += padding
    input_mask += padding
    segment_ids += padding

    assert len(input_ids) == max_seq_length
    assert len(input_mask) == max_seq_length
    assert len(segment_ids) == max_seq_length

    input_features = InputFeatures(
        input_ids=input_ids,
        input_mask=input_mask,
        segment_ids=segment_ids,
        label_id=None
    )
    all_input_ids = torch.tensor([input_features.input_ids], dtype=torch.long)
    all_input_mask = torch.tensor([input_features.input_mask], dtype=torch.long)
    all_segment_ids = torch.tensor([input_features.segment_ids], dtype=torch.long)
    # all_label_ids = torch.tensor([input_features.label_id ], dtype=torch.long)
    return all_input_ids, all_input_mask, all_segment_ids


def classify(trans_new, trans_old, data):
    app.logger.debug("Classifying %s / %s", trans_new, trans_old)
    global model
    input_data = prepareData(trans_new, trans_old)
    input_ids = input_data[0].to(device)
    input_mask = input_data[1].to(device)
    segment_ids = input_data[2].to(device)
    # predictions = model(input_data[0]input_ids, input_data.segment_ids, input_data.input_mask, labels=None)
    predictions = model(input_ids, segment_ids, input_mask, labels=None)
    values1, indices1 = predictions[0][0].max(0)
    data["mostLikely"] = label_list[indices1]
    intents = {}
    for j in range(len(predictions[0][0])):
        label = label_list[j]
        prob = predictions[0][0][j]
        intents[label] = prob.item()
    #data["Intents"] = intents
    secondProb = intents[sorted(intents, key=lambda x: x[1])[2]]
    bestProb = values1.item()
    data["bestProb"] = bestProb
    data["secondProb"] = secondProb
    return data


def init(rootdir):
    # load the trained model
    global model
    global device
    global tokenizer

    device = torch.device("cpu")
    print("device: {}".format(device))

    mod_dir = rootdir + model_dir
    tokenizer = BertTokenizer.from_pretrained(mod_dir, do_lower_case=False)

    num_labels = 42

    model = BertForSequenceClassification.from_pretrained(mod_dir, num_labels=num_labels)
    model = model.to(device)
    model.eval()



if __name__ == "__main__":
    print(("* Loading Bert Model and Flask starting server..."
           "please wait until server has fully started"))
    root = os.path.dirname(sys.argv[0])
    if len(sys.argv) > 1:
        root = sys.argv[1]
    init(root)
    print("loading completeted")
    app.run(host=host, port=port, debug=False)
