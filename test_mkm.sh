#!/bin/bash

# This is supposed to test that all functionality that occurs during a
# conversation is produced in a proper way, provided all supporting modules
# work perfectly. We test this by means of a series of MQTT messages that
# simulate their output during such a conversation, and check the final output.

# The conversation is as follows:
# Speaker A: Zugführer von Gruppenführer kommen. --> AsrResult, unknown Speaker
# MKM: The speaker is <mkm:GF> (is somebody created as addressee?)
# Speaker B: Zugführer hört --> AsrResult, unknown Speaker
# MKM: The speaker is ...
# Speaker A: Für sie: Angriffstrupp mit der Drehleiter zur Rettung verletzter Personen in den ersten Stock --> ASRResult, NLU result with slots, Speaker recognized (?!)
# Speaker B: Angriffstrupp mit der Drehleiter zur Rettung verletzter Personen in den ersten Stock, verstanden
# Speaker A: Das ist so korrekt, Ende

# The test MQTT messages and pipeline config are in the src/test/resources
# folder

# Be aware that start_nlu.sh can take a while to return, you'll see a counter
# running up, and if you get exitcode 2, there was a timeout

#set -x

pom_version() {
    if test -n "$1"; then cd "$1"; fi
    # There are deprecation warnings under the hood!
    mvn help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null
}

# Does not work
test_mkm_docker() {
    if docker images 2>&1 | grep -q mkm:$(pom_version); then
        DOCKER_ARGS="--rm -d --name 'test_mkm'" ./run_docker.sh test_config.yml
        until docker logs test_mkm 2>&1 | grep -q 'sample_rate: 16000'; do
            sleep 3
        done
    fi
}

run_mosquitto() {
    (docker container ls | grep -q mqtt-broker ||
        docker run --name mqtt-broker -d --rm -p 1883:1883 -p 9001:9001 \
               -v `pwd`/configs/mosquitto.conf:/mosquitto/config/mosquitto.conf:ro \
               eclipse-mosquitto:2.0.15
    ) 2>/dev/null
}

kill_mosquitto() {
    docker container ls | grep -q mqtt-broker && docker kill mqtt-broker
}

test_mkm() {
    mvn clean && ./compile && mvn install && java --class-path target/mkm-fatjar.jar de.dfki.mlt.drz.mkm.TestPipeline || return 1
}

run_mosquitto

exitcode=0
if ./start_nlu.sh; then
    test_mkm
    exitcode=$?
else
    exitcode="NLU does not start up"
fi
if test "$exitcode" = "0"; then
    echo "Success!"
else
    echo "Failure: $exitcode"
fi

kill_mosquitto

./stop_nlu.sh
exit $exitcode
