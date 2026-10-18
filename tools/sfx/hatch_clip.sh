#!/bin/sh
# Builds docs/pets_new/hatch_animation.mp4: the full egg hatch with its sound (0.45.2).
#   ./gradlew :app:testDebugUnitTest -PrenderPreviews --tests '*HatchClip*'   (frames)
#   python3 tools/sfx/pet_hatch.py                                            (sound)
# The sound starts at the burst, 1.7 s in.
set -e
ffmpeg -y -framerate 30 -i app/build/hatch_frames/f_%03d.png -i app/src/main/resources/sfx/pet_hatch.wav \
  -map 0:v -map 1:a -af "adelay=1700:all=1,apad" -vf "scale=540:-2,format=yuv420p" \
  -c:v libx264 -crf 20 -c:a aac -b:a 128k -t 5 -movflags +faststart docs/pets_new/hatch_animation.mp4
