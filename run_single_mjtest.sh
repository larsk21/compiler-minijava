#!/bin/bash

echo "[info] eading path, copying files and running mjtest"

export PATH=$PATH
export JAVA_HOME=$JAVA_HOME
export MJ_TIMEOUT=15
export MJ_RUN='./run'

# copy test files
rm -rf ./mjtest/tests/*
mkdir ./mjtest/tests/compile

echo got "$#" arguments

for var in "$@"
do
	if [[ ! -f $var ]]
	then
		echo "[error] $var is not a file"
		exit 1
	fi

	MJ_TEST_TARGET=$(sed 's/mjtest-files\/\(.*\)/\.\/mjtest\/tests\/\1/g' <(echo $var))
	MJ_DIRNAME=$(dirname $MJ_TEST_TARGET)

	if [[ ! -d $MJ_DIRNAME ]] 
	then
		echo "[info] creating directory $MJ_DIRNAME"
		mkdir -p $MJ_DIRNAME
	fi
	echo [info] copying test file $var to mjtest file $MJ_TEST_TARGET
	cp $var $MJ_TEST_TARGET
done

# call mjtest with the specified mode
./mjtest/mjt.py --log_level info compile-firm
return_code=$?

# cleanup
rm -rf ./mjtest/tests/*
exit $return_code
