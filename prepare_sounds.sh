#!/usr/bin/env bash
# Приводит звуки к формату игры: OGG, моно, 44,1 кГц, пик -3 дБ, тишина по краям обрезана.
# Использование:
#   1) положите исходные файлы (wav/mp3/ogg/flac/m4a) в папку input/
#   2) назовите их так, как в sounds_checklist.csv (например animal_dog_1.mp3)
#   3) запустите: bash prepare_sounds.sh
#   4) готовые файлы появятся в папке output/ , их копируйте в app/src/main/res/raw/
# Файлы с именем, оканчивающимся на _loop, не обрезаются и не получают fade (цикл надо проверить на слух).
set -u
IN="${1:-input}"; OUT="${2:-output}"
command -v ffmpeg >/dev/null || { echo "Нужен ffmpeg (https://ffmpeg.org)"; exit 1; }
mkdir -p "$OUT"
shopt -s nullglob nocaseglob
for f in "$IN"/*.wav "$IN"/*.mp3 "$IN"/*.ogg "$IN"/*.flac "$IN"/*.m4a; do
  base=$(basename "$f"); name="${base%.*}"
  name=$(echo "$name" | tr 'A-Z ' 'a-z_' | tr -cd 'a-z0-9_')
  if [[ "$name" == *_loop ]]; then
    PRE="anull"
  else
    SIL="silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.02"
    PRE="$SIL,areverse,$SIL,afade=t=in:d=0.008,areverse,afade=t=in:d=0.005"
  fi
  # проход 1: ищем максимальную громкость после обрезки
  MAX=$(ffmpeg -hide_banner -i "$f" -af "$PRE,volumedetect" -f null - 2>&1 | grep -o 'max_volume: [-0-9.]*' | awk '{print $2}')
  [ -z "$MAX" ] && { echo "ПРОПУЩЕН (не удалось прочитать): $base"; continue; }
  GAIN=$(awk -v m="$MAX" 'BEGIN{printf "%.2f", -3 - m}')
  ffmpeg -hide_banner -loglevel error -y -i "$f" -af "$PRE,volume=${GAIN}dB,alimiter=limit=0.7:level=disabled" \
    -ac 1 -ar 44100 -c:a libvorbis -q:a 4 "$OUT/$name.ogg" && \
  DUR=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$OUT/$name.ogg" | awk '{printf "%.2f", $1}')
  echo "OK  $name.ogg  ${DUR}s  (усиление ${GAIN} дБ)"
  awk -v d="$DUR" -v n="$name" 'BEGIN{ if (d>3.0) print "    ! файл длиннее 3 с: " n " (для SoundPool лучше короче)" }'
done
echo "Готово. Проверьте на слух громкость и стыки у файлов *_loop."
