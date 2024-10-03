[![mvn build](https://github.com/bkiefer/mkm/actions/workflows/maven.yml/badge.svg)](https://github.com/bkiefer/mkm/actions/workflows/maven.yml)

# DRZ Mission Knowledge Manager

## Installation instructions:

### Linux
These instructions are for Linux-Systems (concretely: Ubuntu 22.04 or higher).

In order to compile this, make sure the VOnDA compiler `vondac` (version 3) is available the default path, or adapt the `compile` script in this directory.

To get the VOnDA compiler, check it out from github and compile it (needs Java 11 and maven installed)

```
git clone git@github.com:bkiefer/vonda.git
cd vonda
mvn -U clean install
```

Now the `vondac` script is ready to run in the `bin/` directory

The example can now be build as follows:

```
# generate .java files from the .rudi source files
./compile
# compile the .java files into an executable .jar
mvn install
# start the MKM
./run_mkm
```
**Attention:** Make sure to **not** run `mvn clean install`, as this would delete the generated Java files!

## Evaluation based on a list of transcribed utterances

With the script `testclient.py`, a text file is sent line by line to the MKM. The results are stored in the database, which means defining a persistence file will provide all generated information in the sequence in which it is created.

For convenience, a CSV file can be generated that is easier to use for evaluation purposes. It contains the following informations, if they could be computed:

id, sender, addressee, dialogue intent

id is always present, this is the line number of the text string sent.

## Evaluation based on a list of transcribed utterances with id and optional speaker

`testcsvclient.py` needs a .csv file with appropriate information and works similar to `testclient.py`. It sends the message id with the text and optionally also the speaker information to simulate an ideal speaker recognition module.
