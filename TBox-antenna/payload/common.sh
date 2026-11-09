#!/bin/sh
BASE=/usr/local/lib/TBox-antenna
# D-Bus serialization escapes CR/LF/quotes. Do not eval modem output.
decode_at() { sed 's/\\r/ /g;s/\\n/\
/g;s/\\"/"/g'; }
at() {
 raw=$(busctl --address=unix:path=/tmp/.hirain-dbus --timeout=8 call gdbus.hcommon.rild /gdbus/hcommon/rild gdbus.hcommon.rild.modem sendAtSync s "$1" 2>&1) || return 1
 reply=$(printf '%s\n' "$raw" | decode_at)
 case "$reply" in *ERROR*) return 1;; esac
 printf '%s\n' "$reply" | grep -Eq '(^|[[:space:]])OK([[:space:]]|$)'
}
fail() { echo "ERROR: $*"; exit 1; }
hash() { sha256sum "$1" | awk '{print $1}'; }
valid_ip() { printf '%s\n' "$1" | awk -F. 'NF!=4{exit 1}{for(i=1;i<=4;i++)if($i!~/^[0-9]+$/||$i>255)exit 1;if($1==0||$1==127||$1>=224)exit 1}'; }
wait_at() { n=0; until at 'AT+CFUN?'; do n=$((n+1)); [ "$n" -lt 12 ] || return 1; sleep 2; done; }
remove_network() {
 iptables -t nat -D POSTROUTING -j VI_KIT_OUT 2>/dev/null || true
 iptables -t nat -D PREROUTING -i eth2 -j VI_KIT_DNS 2>/dev/null || true
 iptables -t nat -F VI_KIT_OUT 2>/dev/null || true
 iptables -t nat -X VI_KIT_OUT 2>/dev/null || true
 iptables -t nat -F VI_KIT_DNS 2>/dev/null || true
 iptables -t nat -X VI_KIT_DNS 2>/dev/null || true
 if [ -f "$BASE/route-added" ]; then
  dev=$(cat "$BASE/route-added")
  case "$dev" in ccmni[0-9]*) ip route del default dev "$dev" metric 709 2>/dev/null || true;; esac
  rm -f "$BASE/route-added"
 fi
}
