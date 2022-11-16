#!/bin/bash

JAVA=/home/ubuntu/.jdks/openjdk-17.0.1/bin/java
REF=./refspl

for t in spl-testfiles/*/*.spl; do
  printf "\x1B[0mTesting \033[3m%s\x1B[0m .... " $t;
  $JAVA -cp spl.jar de.thm.mni.compilerbau.Main $1 $t > output1.txt 2>output_error1.txt;
  $REF $1 $t > output2.txt 2>output_error2.txt;

  if ! diff output1.txt output2.txt > log.txt 2>&1; then
    printf "\033[31m✗\n";
    printf "\x1B[0mSee \033[3mlog.txt \x1B[0mfor details\n"
    break;
  fi

  if ! diff output_error1.txt output_error2.txt > log.txt 2>&1; then
    printf "\033[31m✗\n";
    printf "\x1B[0mSee \033[3mlog.txt \x1B[0mfor details\n"
    break;
  fi

  # every output is equal
  printf "\033[32m✓\n";
done;
rm output*.txt