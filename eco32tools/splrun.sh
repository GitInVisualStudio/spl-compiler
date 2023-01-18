#!/bin/bash

if [ "$#" -ne 1 ]; then
    echo "Illegal number of parameters."
    echo "Usage: $0 <path_to_spl_file>"
    exit 1
fi

spl_file_path=$1
without_extension=${spl_file_path%.*}

SCRIPTDIR=$(dirname "$0")

$SCRIPTDIR/compile.sh $@ || exit 
$SCRIPTDIR/run.sh $without_extension.bin