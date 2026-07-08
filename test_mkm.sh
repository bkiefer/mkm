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

# The test MQTT messages and pipeline config are in the src/test/resources folder
# Be aware that it can take a while for the supporting docker containers to start,
# You'll see a counter running up, and if you get exitcode 2, they don't come up
# quickly enough, or not at all.

#set -x
./rasa/rasadock
cd modules/drz_intentslot
DOCKER_ARGS="-d --rm --name drz_intentslot" ./run_docker.sh
cd ../../

shutdown() {
    (docker kill "mkm_rasa_nlu"
     docker kill "drz_intentslot"
     docker container prune -f) >/dev/null 2>/dev/null
}

rasa_alive() {
    count=0
    while test "$count" \!= "8"; do
        if $(docker logs mkm_rasa_nlu 2>&1 | grep -q 'Rasa server is up and running'); then
            break;
         else
            sleep 5
            count="$(($count + 1))"
            echo -n $count
        fi
    done
    return $(test $count -le 7)
}

intentslot_alive() {
    count=0
    while test "$count" -le "8"; do
        if test "$(curl http://localhost:5050/alive 2>/dev/null)" \
                = 'tag server is alive'; then
            break
        else
            sleep 5
            count="$(($count + 1))"
            echo -n $count
        fi
    done
    return $(test $count -le 7)
}

exitcode=0
if intentslot_alive && rasa_alive; then
    mvn clean && ./compile && mvn install && java --class-path target/mkm-fatjar.jar de.dfki.mlt.drz.mkm.TestPipeline || exitcode=1
else
    exitcode=2
fi
if test "$exitcode" = "0"; then
    echo "Success!"
else
    echo "Failure: $exitcode"
fi

shutdown
exit $exitcode
