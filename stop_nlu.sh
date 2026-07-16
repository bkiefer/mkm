#!/bin/bash

shutdown() {
    (docker kill "mkm_rasa_nlu"
     docker kill "drz_intentslot"
     docker container prune -f) >/dev/null 2>/dev/null
}

shutdown
