#!/bin/bash

#CFR .jar file
BINARY=$1

#FOLDER TO DECOMPILE
FOLDER=$2

#OUTPUT WHERE DECOMPILE
OUTPUT=$3

if [[ $# -lt 3 ]] ; then
    echo 'Please provide: <path to cfr jar> <path to folder to analyze> <output folder>'
    exit 1
fi

for f in $FOLDER/* $FOLDER/**/* ; do
    if [[ -d $f ]]; then
        echo "$f is a directory"
    elif [[ -f $f ]]; then
        java -jar $BINARY $f --outputdir $OUTPUT
    fi
done;
