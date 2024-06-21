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
