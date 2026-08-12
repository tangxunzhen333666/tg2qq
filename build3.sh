#!/bin/bash
# aapt2 构建：compile 资源 + link（生成有效 arsc）+ dex + 签名
set -e
SDK=$HOME/android-sdk
BT=$SDK/build-tools/35.0.0
PLATFORM=$SDK/platforms/android-28/android.jar
MOD=~/tgwork/module
OUT=~/tgwork/out
KS=/sdcard/Download/TG2QQ/keystore.jks
KS_PASS=tg2qq123
rm -rf $OUT && mkdir -p $OUT/classes $OUT/stubclasses $OUT/dex $OUT/apk $OUT/res
# 1. 编译 stub
javac -source 17 -target 17 -cp $PLATFORM -d $OUT/stubclasses \
    $(find $MOD/stub -name '*.java')
# 2. 编译模块源码
javac -source 17 -target 17 -cp $PLATFORM:$OUT/stubclasses -d $OUT/classes \
    $(find $MOD/src -name '*.java')
# 3. d8 转 dex
$BT/d8 --release --lib $PLATFORM --min-api 26 --output $OUT/dex \
    $(find $OUT/classes/com/operit -name '*.class')
# 4. aapt2 compile 资源
aapt2 compile --dir $MOD/res -o $OUT/res/compiled.zip
# 5. aapt2 link（manifest + 编译资源 + assets）
aapt2 link -o $OUT/apk/base.apk --manifest $MOD/AndroidManifest.xml\
    -I $PLATFORM -A $MOD/assets --min-sdk-version 26 --target-sdk-version 34 \
    $OUT/res/compiled.zip
# 6. 加入 classes.dex（ZIP_STORED）
cd $OUT/dex && python3 -c "
import zipfile
z = zipfile.ZipFile('$OUT/apk/base.apk', 'a', zipfile.ZIP_STORED)
z.write('classes.dex', 'classes.dex')
z.writestr('META-INF/xposed/java_init.list', 'com.operit.tg2qq.HookEntry\n')
z.close()
"
# 7. 签名
[ -f $KS ] || keytool -genkeypair -keystore $KS -alias tgf -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass $KS_PASS -keypass $KS_PASS -dname "CN=TGForward"
$BT/apksigner sign --ks $KS --ks-pass pass:$KS_PASS --key-pass pass:$KS_PASS \
    --out $OUT/tgforward.apk $OUT/apk/base.apk
ls -la $OUT/tgforward.apk
echo BUILD_OK