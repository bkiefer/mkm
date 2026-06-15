# NLU intent analysis module based on rasa NLU
This module is using the official docker container from rasa, supplemented with a script to facilitate usage and training.

## Run a rasa server in background on port 9797, using the CPU for inference

    ./rasadock

See the `./test_rasa` script for usage and possible input data.

## Train NLU with all data from the `data` direcory
Defaults: training files are in `data`, config (pipeline def) in `config.yml`

`./rasadock train --num-threads=32`

## Shuffle and split data: results in `train_test_split`
`./rasadock data split nlu`

## Train NLU with the train split of the data
`./rasadock train nlu --num-threads=16 --nlu train_test_split/training_data.yml`

## Test performance on test split of data
`./rasadock test nlu --nlu train_test_split/test_data.yml`

## Larger cross-validation test
`./rasadock test nlu --nlu data/nlu --cross-validation --folds 5`

## Comparing different pipeline configurations
`./rasadock test nlu --nlu data/univfaq.yml --config config.yml config2.yml`
