#!/bin/bash
# run_all.sh <dir_in> <dir_out>: вырезает все *.jpg (по порядку имён) в NN.png
i=0; for f in $(ls "$1"/*.jpg | sort); do
  python3 -I "$(dirname "$0")/cutout.py" "$f" "$2/$(printf %02d $i).png" >> "$2/log.txt" 2>&1 & i=$((i+1)); [ $((i%6)) -eq 0 ] && wait
done; wait
