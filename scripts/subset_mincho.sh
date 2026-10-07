#!/usr/bin/env sh
# Subsets Zen Old Mincho for app/src/main/res/font.
#
# Usage:
#   pip install fonttools
#   curl -LO https://raw.githubusercontent.com/google/fonts/main/ofl/zenoldmincho/ZenOldMincho-Black.ttf
#   curl -LO https://raw.githubusercontent.com/google/fonts/main/ofl/zenoldmincho/ZenOldMincho-SemiBold.ttf
#   scripts/subset_mincho.sh .
#
# Keeps Basic Latin, Latin-1, dashes, quotes and the ellipsis, plus the kanji the UI uses
# (colourway names, 伝統色, 夜, 次, 切符). Add kanji to KANJI when the UI gains more.
set -eu

SRC=${1:-.}
OUT="$(dirname "$0")/../app/src/main/res/font"
KANJI="藍抹茶桜藤柿墨伝統色夜次切符"

for weight in Black SemiBold; do
    lower=$(echo "$weight" | tr '[:upper:]' '[:lower:]')
    pyftsubset "$SRC/ZenOldMincho-$weight.ttf" \
        --unicodes="U+0020-007E,U+00A0-00FF,U+2013,U+2014,U+2018,U+2019,U+201C,U+201D,U+2026" \
        --text="$KANJI" \
        --layout-features='kern,liga,palt' \
        --no-hinting --desubroutinize --name-IDs='*' \
        --output-file="$OUT/zen_old_mincho_$lower.ttf"
done
