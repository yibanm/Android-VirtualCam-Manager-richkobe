#!/system/bin/sh
ui_print "- VirtualCam Manager v2.2.0 (pure Magisk + Zygisk, NO LSPosed)"
ui_print "- Camera1 surface swap + MediaPlayer + GL fallback"
ui_print "- Magisk → Settings → Zygisk ON, then reboot"

mkdir -p /data/adb/virtualcam
chmod 755 /data/adb/virtualcam
echo -n 0 > /data/adb/virtualcam/enabled
echo -n "2.2.0" > /data/adb/virtualcam/module_version
echo -n "2.2.0" > /data/adb/virtualcam/version
touch /data/adb/virtualcam/module_installed
chmod 644 /data/adb/virtualcam/* 2>/dev/null

mkdir -p /data/local/tmp/virtualcam
chmod 755 /data/local/tmp/virtualcam

ABI=$(getprop ro.product.cpu.abi)
if [ -f "$MODPATH/libs/$ABI/libshadowhook.so" ]; then
  cp -f "$MODPATH/libs/$ABI/libshadowhook.so" "$MODPATH/libshadowhook.so"
  cp -f "$MODPATH/libs/$ABI/libshadowhook.so" /data/local/tmp/virtualcam/libshadowhook.so
  chmod 755 /data/local/tmp/virtualcam/libshadowhook.so
  ui_print "- ShadowHook installed for $ABI"
elif [ -f "$MODPATH/libshadowhook.so" ]; then
  cp -f "$MODPATH/libshadowhook.so" /data/local/tmp/virtualcam/libshadowhook.so
  chmod 755 /data/local/tmp/virtualcam/libshadowhook.so
  ui_print "- ShadowHook present"
else
  ui_print "! ShadowHook missing — hooks stay pending until CI zip is used"
fi

if [ -f "$MODPATH/zygisk/arm64-v8a.so" ] || [ -f "$MODPATH/zygisk/armeabi-v7a.so" ]; then
  ui_print "- Zygisk libraries packaged"
else
  ui_print "! Warning: no zygisk/*.so — use the GitHub Actions zip"
fi

mkdir -p /storage/emulated/0/DCIM/Camera1 2>/dev/null
chmod 775 /storage/emulated/0/DCIM/Camera1 2>/dev/null

ui_print "- After reboot: install Manager APK → grant root → pick media → ON"
ui_print "- Open a Camera1 app first (Open Camera / many stock cameras)"
