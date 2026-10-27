#!/data/data/com.termux/files/usr/bin/sh
set -eu
cd "$(dirname "$0")"
ganime_sdk="${GANIME_SDK_DIR:-../pulse-player/tools}"
ganime_key="${GANIME_KEYSTORE:-tools/g-anime.keystore}"
ganime_password="${GANIME_SIGNING_PASSWORD_FILE:-tools/signing-password}"
for ganime_file in "$ganime_sdk/android.jar" "$ganime_sdk/android-res.jar" "$ganime_sdk/r8.jar" "$ganime_key" "$ganime_password" signing-certificate.sha256; do
    if [ ! -f "$ganime_file" ]; then echo "缺少构建文件：$ganime_file" >&2; exit 1; fi
done
ganime_cert=$(keytool -exportcert -keystore "$ganime_key" -storepass:file "$ganime_password" -alias ganime 2>/dev/null | sha256sum | cut -d ' ' -f 1)
if [ "$ganime_cert" != "$(cat signing-certificate.sha256)" ]; then echo '签名指纹不匹配，停止构建。' >&2; exit 1; fi
mkdir -p build/generated build/classes build/dex
find build/classes -type f -name '*.class' -delete
aapt2 compile --dir res -o build/res.zip
aapt2 link -I "$ganime_sdk/android-res.jar" --manifest AndroidManifest.xml --java build/generated -o build/resources.apk build/res.zip
javac --release 8 -encoding UTF-8 -classpath "$ganime_sdk/android.jar" -d build/classes src/cn/g/anime/*.java build/generated/cn/g/anime/R.java
jar cf build/classes.jar -C build/classes .
java -Xmx512m -cp "$ganime_sdk/r8.jar" com.android.tools.r8.D8 --lib "$ganime_sdk/android.jar" --min-api 26 --output build/dex build/classes.jar
cp build/resources.apk build/unsigned.apk
jar uf build/unsigned.apk -C build/dex classes.dex
zipalign -f 4 build/unsigned.apk build/aligned.apk
apksigner sign --ks "$ganime_key" --ks-pass "file:$ganime_password" --out build/G-anime-0.2.apk build/aligned.apk
apksigner verify --verbose build/G-anime-0.2.apk
zipalign -c 4 build/G-anime-0.2.apk
