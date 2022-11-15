#!/bin/bash

JAVA=/home/ubuntu/.jdks/openjdk-17.0.1/bin/java;
REF=./refspl

for t in spl-testfiles/*/*.spl; do
  printf "\x1B[0mTesting \033[1m$t\x1B[0m.... ";
  $JAVA -cp spl.jar de.thm.mni.compilerbau.Main $1 $t > output1.txt 2>&1;
  $REF $1 $t > output2.txt 2>&1;
  if diff output1.txt output2.txt > /dev/null 2>&1; then
    printf "\033[32m✓\n";
  else
    printf "\033[31m✗\n";
    break;
  fi
done;
rm output1.txt output2.txt