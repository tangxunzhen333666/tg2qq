#!/bin/bash
# 零依赖构建：javac + d8 + aapt2 + zipalign + apksigner
set -x
SDK=${SDK:-$HOME/android-sdk}
BT=$SDK/build-tools/35.0.0
PLATFORM=$SDK/platforms/android-35/android.jar
MOD=~/tgwork/module
OUT=~/tgwork/out
KS=~/tgwork/keystore.jks
KS_PASS=tgforward

rm -rf $OUT && mkdir -p $OUT/classes $OUT/stubclasses $OUT/dex $OUT/apk

# 1. 编译 stub（仅编译期，不进 APK）
javac -source 17 -target 17 -cp $PLATFORM -d $OUT/stubclasses \
    $(find $MOD/stub -name '*.java') || exit 1

# 2. 编译模块源码
javac -source 17 -target 17 -cp $PLATFORM:$OUT/stubclasses -d $OUT/classes \
    $(find $MOD/src -name '*.java') || exit 1

# 3. d8 转 dex（只包含模块自身类，排除 stub）
$BT/d8 --release --lib $PLATFORM --min-api 26 --output $OUT/dex \
    $(find $OUT/classes/com/tgforward -name '*.class') || exit 1

# 4. aapt1 打包（manifest + assets；aapt1 用旧版 framework jar 解析）
AAPT_PLATFORM=$SDK/platforms/android-28/android.jar
aapt p -f -M $MOD/AndroidManifest.xml -I $AAPT_PLATFORM -A $MOD/assets \
    -F $OUT/apk/base.apk || exit 1

# 5. 加入 classes.dex（ZIP_STORED 不压缩，兼容低版本系统）
cd $OUT/dex && python3 -c "
import zipfile
z = zipfile.ZipFile('$OUT/apk/base.apk', 'a', zipfile.ZIP_STORED)
z.write('classes.dex', 'classes.dex')
z.close()
" || exit 1

# 6. 签名（apksigner 为 Java 实现，签名时自动对齐 zip 条目，无需 zipalign）
[ -f $KS ] || keytool -genkeypair -keystore $KS -alias tgf -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass $KS_PASS -keypass $KS_PASS -dname "CN=TGForward" || exit 1
$BT/apksigner sign --ks $KS --ks-pass pass:$KS_PASS --key-pass pass:$KS_PASS \
    --out $OUT/tgforward.apk $OUT/apk/base.apk || exit 1

ls -la $OUT/tgforward.apk
echo BUILD_OK