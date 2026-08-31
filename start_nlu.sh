#!/bin/bash
# Be aware that it can take a while for the supporting docker containers to
# start!

# You'll see a counter running up, and if you get exitcode 2, they don't come
# up quickly enough, or not at all.

#set -x

rasa_alive() {
    count=0
    while test "$count" -le "10"; do
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
    while test "$count" -le "10"; do
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

scrdir=`dirname $0`
cd "$scrdir"
./rasa/rasadock
cd modules/drz_intentslot
DOCKER_ARGS="-d --rm --name drz_intentslot" ./run_docker.sh

intentslot_alive && rasa_alive
