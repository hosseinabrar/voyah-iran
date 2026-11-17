#!/bin/sh
BASE=/usr/local/lib/TBox-antenna
. "$BASE/common.sh"
rm -f "$BASE/enabled" "$BASE/ready"
remove_network
if [ -f "$BASE/owned-context" ]; then
 cid=$(cut -d '|' -f1 "$BASE/owned-context")
 apn=$(cut -d '|' -f2 "$BASE/owned-context")
 if at 'AT+CGDCONT?'; then
  current=$(printf '%s\n' "$reply" | awk -F, -v c="$cid" '$1~("\\+CGDCONT: *"c"$"){gsub(/[" ]/,"",$3);print $3;exit}')
  if [ "$current" = "$apn" ]; then
   case "$cid" in 1|2|3|4|5|6|7|8|9|10|11|12|13|14|15|16)
    at "AT+CGACT=0,$cid" && at "AT+CGDCONT=$cid" && rm -f "$BASE/owned-context";;
   esac
  fi
 fi
fi
if [ -f "$BASE/forward-before" ]; then cat "$BASE/forward-before" > /proc/sys/net/ipv4/ip_forward; fi
if [ -f "$BASE/signal-mounted" ]; then
 umount /mnt/hirain_app/usr/bin/hrild && rm -f "$BASE/signal-mounted"
 pid=$(pidof hrild); [ -z "$pid" ] || kill -TERM $pid
fi
if [ -f "$BASE/radio-mounted" ]; then
 umount /lib64/libvendor-ril.so && rm -f "$BASE/radio-mounted"
 systemctl restart ql-rild.service
fi
