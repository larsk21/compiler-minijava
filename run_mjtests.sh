#!/bin/bash

cd ..

echo "Reading User Path and running mjtests"

export PATH=$(cat ./compiler-minijava/userpath)
export JAVA_HOME=$(cat ./compiler-minijava/userhome)
export MJ_TIMEOUT=10
export MJ_RUN='./compiler-minijava/run'
./mjtest/mjt.py --log_level  error --ci_testing --parallel lexer