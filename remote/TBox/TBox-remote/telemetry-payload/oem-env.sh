# Sourced by our services only; never changes the factory environment.
export PATH=/mnt/hirain_app/usr/bin:/mnt/hirain_app/usr/sbin:/usr/bin:/usr/sbin:/bin:/sbin
export LD_LIBRARY_PATH=/mnt/hirain_app/openssl_lib:/mnt/hirain_app/usr/lib:/lib:/usr/lib:/usr/lib64:/lib64
export HIRAIN_PATH_CONF=/mnt/hirain_conf HIRAIN_PATH_DATA=/mnt/hirain_data
export HIRAIN_PATH_EXT_DATA=/mnt/hirain_extdata HIRAIN_PATH_APP=/mnt/hirain_app
if [ -S /tmp/.hirain-dbus ]; then
 export VI_OEM_BUS_ADDRESS=unix:path=/tmp/.hirain-dbus
else
 for process in rild hrild vnsd; do
  for pid in $(pidof "$process" 2>/dev/null || true); do
   address=$(tr '\000' '\n' <"/proc/$pid/environ" | sed -n 's/^DBUS_SESSION_BUS_ADDRESS=//p' | head -1)
   case "$address" in unix:abstract=*|unix:path=*) export VI_OEM_BUS_ADDRESS="${address%%,*}"; break 2;; esac
  done
 done
fi
export DBUS_SESSION_BUS_ADDRESS="${VI_OEM_BUS_ADDRESS:-unix:path=/tmp/.hirain-dbus}"
