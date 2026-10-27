#!/data/data/com.termux/files/usr/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p build/test-classes
javac --release 8 -encoding UTF-8 -d build/test-classes src/cn/g/anime/Playback.java src/cn/g/anime/Source.java tests/PlaybackTest.java
java -cp build/test-classes PlaybackTest
if [ -f tools/json.jar ]; then
 javac --release 8 -encoding UTF-8 -cp tools/json.jar:build/test-classes -d build/test-classes src/cn/g/anime/Api.java src/cn/g/anime/Catalog.java tests/CatalogTest.java
 java -cp build/test-classes:tools/json.jar CatalogTest
else
 echo 'Catalog checks require org.json 20240303 in tools/json.jar' >&2
 exit 1
fi
if [ "${1:-}" = '--live' ]; then
 if [ ! -f tools/json.jar ]; then echo 'Live check requires org.json 20240303 in tools/json.jar' >&2; exit 1; fi
 javac --release 8 -encoding UTF-8 -cp tools/json.jar -d build/test-classes src/cn/g/anime/Api.java tests/LiveCheck.java src/cn/g/anime/Playback.java src/cn/g/anime/Source.java
 java -cp build/test-classes:tools/json.jar LiveCheck
fi
