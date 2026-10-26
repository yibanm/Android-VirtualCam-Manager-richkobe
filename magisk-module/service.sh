#!/system/bin/sh
MODDIR=${0%/*}
sleep 8
mkdir -p /data/adb/virtualcam
mkdir -p /data/local/tmp/virtualcam
mkdir -p /storage/emulated/0/DCIM/Camera1 2>/dev/null
chmod 775 /storage/emulated/0/DCIM/Camera1 2>/dev/null
chmod 755 /data/local/tmp/virtualcam
restorecon -RF /storage/emulated/0/DCIM/Camera1 2>/dev/null
echo -n "2.2.1" > /data/adb/virtualcam/module_version
echo -n "2.2.1" > /data/adb/virtualcam/version
chmod 644 /data/adb/virtualcam/module_version 2>/dev/null
chmod 644 /data/adb/virtualcam/version 2>/dev/null
if [ -f "$MODDIR/libshadowhook.so" ]; then
  cp -f "$MODDIR/libshadowhook.so" /data/local/tmp/virtualcam/libshadowhook.so
  chmod 755 /data/local/tmp/virtualcam/libshadowhook.so
fi
[ -f /data/adb/modules/virtualcam_manager/module.prop ] && touch /data/adb/virtualcam/module_installed
