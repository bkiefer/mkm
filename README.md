[![mvn build](https://github.com/bkiefer/mkm/actions/workflows/maven.yml/badge.svg)](https://github.com/bkiefer/mkm/actions/workflows/maven.yml)

# DRZ Mission Knowledge Manager

## Installation instructions for use with Docker and docker compose

## Prerequisites

These instructions have been tested on **Ubuntu 24.04** and higher, but they should also work on other Linux distributions.

### OS Requirements:

* **Java 11** or higher (`openjdk-11-jdk`)
* **Maven**
* **git** and **git-lfs**

Additionally, the following tools are required:

* **Docker** and **Docker Compose**
* **NVIDIA Container Toolkit**
  (For installation, it's recommended to follow the official procedure on the Docker website.)

Ensure that the user installing the software is part of the **docker** group. You can add a user to this group using the following command:

```bash
sudo usermod -aG docker <username>
```

---

## Setup Instructions

After cloning the repository, run the following command to set up the environment:

```bash
./build_modules.sh
```

This script will:

* Pull the necessary submodules
* Build the modules, including Docker images
* Download the required machine learning models for the setup

---

## Configuration

The `configs` directory contains configuration files that may need to be adapted to your specific runtime environment. For example, you may need to adjust settings for your current sound system setup. Detailed explanations can be found within each of the configuration files.

<!--
### TODO:
### Installation of Fraunhofer & Eurocommand connectors (submodule, mvn repo?)
-->

## Bare metal installation (for some modules) DISCOURAGED, NOT VERIFIED

An additional requirement for local installation is the `uv` package manager for python. Make sure `uv` is working on your machine.

Then, run the `./prepare_localinst.sh` script, which will pull all submodules, eventually create new virtual environments, download the necessary ML models, and compile the MKM module.

<!-- Follow the instructions in `modules/asrident` and `modules/drz_intentslot` on how to install these. -->


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
