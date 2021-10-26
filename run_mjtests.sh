#!/bin/bash

echo "Reading path, copying files and running mjtest"

export PATH=$PATH
export JAVA_HOME=$JAVA_HOME
export MJ_TIMEOUT=10
export MJ_RUN='./run'

rm -rf ./mjtest/tests/*
cp -r ./mjtest-files/$1 ./mjtest/tests/

./mjtest/mjt.py --log_level  error --ci_testing --parallel $1
