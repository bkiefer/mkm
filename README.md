[![mvn build](https://github.com/bkiefer/mkm/actions/workflows/maven.yml/badge.svg)](https://github.com/bkiefer/mkm/actions/workflows/maven.yml)

# DRZ Mission Knowledge Manager

## Installation instructions:

### Linux
These instructions are for Linux-Systems (concretely: Ubuntu 22.04 or higher).

### TODO: dialogue ontology is currently a link! needs own repo & submodule.
### Installation of Fraunhofer & Eurocommand connectors (submodule, mvn repo?)
### Installation of F&E & vonda needs javac! (vonda only tests)

```
git submodule update --init --recursive
git submodule update --recursive --remote
```

In order to compile this, make sure the VOnDA compiler `vondac` (version 3) is available the default path, or adapt the `compile` script in this directory.

To get the VOnDA compiler, check it out from github and compile it (needs Java 11 and maven installed)

```
git clone git@github.com:bkiefer/vonda.git
cd vonda
mvn -U clean install
```

Now the `vondac` script is ready to run in the `bin/` directory, add it to your `PATH` or make sure it can be run otherwise.

The example can now be build as follows:

```
# generate .java files from the .rudi source files (will call mvn clean first)
./compile
# compile the .java files into an executable .jar
mvn install
# start the MKM
./run_mkm
```

**Attention:** Do **not** run `mvn clean install`, as this will delete the generated Java files and compilation will fail.

## Start the complete system, ASR&SpeakerIdent, DA&Slot recognition and MKM

- A MQTT broker must be running with access rights that allow access from the `asrident` and MKM processes. To allow unrestricted access to the broker from docker containers and external computers, put the following lines into the config (into /etc/mosquitto/conf.d/10-external.conf if the linux mosquitto service is used):

```
listener 1883:0.0.0.0
allow_anonymous true
```

- the [asrident](https://github.com/bkiefer/asrident) module must be installed and started. It can run on a remote computer, but then the MQTT broker needs to accept connections from that computer at least. Also the whisper ASR can be started on its own computer, since that module also allows to use a remote ASR.

The config must be adapted accordingly, and possibly the MQTT broker config.

- the [drz_intentslot](https://github.com/bkiefer/drz_intentslot) module must be installed and started . It's providing a web socket, which also allows remote access, with the usual requirements (firewalls, etc.). Also, the host and port of the module must be put into the MKM's config.

Models for drz_intentslot can be downloaded at [model.tar.gz](https://cloud.dfki.de/owncloud/index.php/s/RW6f56AwiqgBKem). Unpack them in the root directory of the module.

- the MKM module itself must be started with a config adapted to the overall setup. Especially the `AdapterBertInterpreter` section of `NLU` for the `drz_intent` must be adapted.

## Evaluation based on a list of transcribed utterances

With the script `testclient.py`, a text file is sent line by line to the MKM. The results are stored in the database, which means defining a persistence file will provide all generated information in the sequence in which it is created.

For convenience, a CSV file can be generated that is easier to use for evaluation purposes. It contains the following informations, if they could be computed:

id, sender, addressee, dialogue intent

id is always present, this is the line number of the text string sent.

## Evaluation based on a list of transcribed utterances with id and optional speaker

`testcsvclient.py` needs a .csv file with appropriate information and works similar to `testclient.py`. It sends the message id with the text and optionally also the speaker information to simulate an ideal speaker recognition module.

## Evaluation with real speaker identification module

This runs with real ASR and speaker identification, so audio must be fed to the asrident module using either the file functionality of asrident or via gstreamer. For the MKM, evaluation mode is defined in the config file, specifying an _evaluation_ key with value _true_.
