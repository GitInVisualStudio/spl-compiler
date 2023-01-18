#!/bin/bash

JAVA=/home/ubuntu/.jdks/openjdk-17.0.1/bin/java
REF=./refspl
PHASES=("--tokens" "--parse" "--absyn" "--tables" "--semant" "--vars" "--compile")
passed=true

test() {
  if [ "$args" == "--compile" ]; then
    args="" # compile is actually just no argument at all
  fi

  $JAVA -cp spl.jar de.thm.mni.compilerbau.Main $args $file > output1.txt 2>output_error1.txt;
  $REF $args $file > output2.txt 2>output_error2.txt;

  if ! diff output1.txt output2.txt > log.txt 2>&1; then
    printf "\033[31m✗\n";
    printf "\x1B[0mFailed on stage: %s\n" $args;
    printf "\x1B[0mSee \033[3mlog.txt \x1B[0mfor details\n"
    passed=false;
  fi

  if ! diff output_error1.txt output_error2.txt > log.txt 2>&1; then
    printf "\033[31m✗\n";
    printf "\x1B[0mFailed on stage: %s\n" $args;
    printf "\x1B[0mSee \033[3mlog.txt \x1B[0mfor details\n"
    passed=false;
  fi
}

for t in spl-testfiles/*/*.spl; do
  printf "\x1B[0mTesting \033[3m%s\x1B[0m .... " $t;
  passed=true
  file=$t;
  if ! [ -z "$1" ]
  then
    args=$1;
    test
    if $passed; then
      printf "\033[32m✓" $i;
    else
      break;
    fi;
  else
    for i in ${PHASES[@]}; do
      args=$i;
      test
      if $passed; then
        printf "\033[32m✓" $i;
      else
        break;
      fi;
    done;
  fi

  if ! $passed; then
    break;
  fi
  printf "\n";
done;
rm output*.txt