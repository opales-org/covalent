#!/bin/sh
# Regenerates the diagram SVG files from the Mermaid sources (Mermaid CLI 12, `mmdc`).
# Run it from this folder. It uses GNU sed.
set -e
for d in ports lifecycle; do
  for t in light dark; do
    mmdc -c "$t.json" -b transparent -i "$d.mmd" -o "$d-$t.svg"
    # Mermaid left-aligns the node labels when they are plain SVG text: center them on their boxes.
    sed -E -i 's/class="label" style="" transform="translate\(-[0-9.]+,/class="label" style="" transform="translate(0,/g; s/<text y="-10\.1" style="">/<text y="-10.1" text-anchor="middle" style="">/g' "$d-$t.svg"
  done
done
