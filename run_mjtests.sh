#!/bin/bash

echo "Reading path, copying files and running mjtest"

export PATH=$PATH
export JAVA_HOME=$JAVA_HOME
export MJ_TIMEOUT=60
export MJ_BIG_TIMEOUT=90
export MJ_RUN='./run'

# copy test files
rm -rf ./mjtest/tests/*
cp -r ./mjtest-files/* ./mjtest/tests/

# call mjtest with the specified mode
./mjtest/mjt.py --log_level  error --ci_testing --parallel $1
return_code=$?

# cleanup
rm -rf ./mjtest/tests/*
exit $return_code
