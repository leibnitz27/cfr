#!/bin/bash

#CFR .jar file
BINARY=$1

#EXTENSION TO ANALYZE
EXT=$2

#FOLDER TO DECOMPILE
FOLDER=$3

#OUTPUT WHERE DECOMPILE
OUTPUT=$4

#SORT FILE CONTENT
SORT=$5


if [[ $# -lt 4 ]] ; then
    echo 'Please provide the following parameters: '
    echo '<path to cfr jar> <extension to analyze> <path to folder to analyze> <output folder>'
    echo ''
    echo 'EXAMPLE:'
    echo ''
    echo './cfr-loop.sh ./cfr-0.150.jar .class /path/to/my/project-root /tmp/a-new-or-existing-folder-as-you-want'
    exit 1
fi

echo "decompiling ..."
set -f
for f in $(find "$FOLDER" -name "*$EXT")
do
  java -jar $BINARY $f --outputdir $OUTPUT
done
echo "decompiling done"

if [[ $SORT -eq sort ]] ; then
  echo "sorting ..."
  set -f
  for fjava in $(find "$OUTPUT" -name *.java)
  do
    sort $fjava --output=$fjava
  done
  echo "sorting done"
fi
