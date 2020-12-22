#!/bin/bash
#developed by marco guassone 2020/12/22

#CFR .jar file
BINARY=$1

#EXTENSION TO ANALYZE
EXT=$2

#FOLDER TO DECOMPILE
FOLDER=$3

#OUTPUT WHERE DECOMPILE
OUTPUT=$4


if [[ $# -lt 4 ]] ; then

    echo ''
    echo ''
    echo 'Please provide the following parameters: '
    echo '<path to cfr jar> <extension to analyze> <path to folder to analyze> <output folder>'
    echo ''
    echo ''
    echo 'EXAMPLE:'
    echo ''
    echo ''
    echo './cfr-loop.sh ./cfr-0.150.jar .class /path/to/my/project-root /tmp/a-new-or-existing-folder-as-you-want'
    exit 1
fi

set -f
for f in $(find "$FOLDER" -name *.class)
do
  java -jar $BINARY $f --outputdir $OUTPUT
done
