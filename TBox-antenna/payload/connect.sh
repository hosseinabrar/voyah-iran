#!/bin/sh
set -eu
# Recheck version on every boot, including after an OEM update.
grep -Eq 'name="TBOX_Software_Version"[[:space:]]+value="2\.0\.7"' /usr/etc/software_versions.xml || exit 1
BASE=/usr/local/lib/TBox-antenna
. "$BASE/common.sh"
exec >>/tmp/voyah-tbox-kit.log 2>&1
rm -f "$BASE/ready"
[ "$(hash "$BASE/libvendor-ril-test.so")" = 1ec44b63fd7dd2a5d0cc88a06fe82c802f2ec415aff87861611b6ed95c6ef201 ] || fail 'Radio payload hash mismatch'
[ "$(hash "$BASE/hrild-signal")" = 0396fb7a3d1cf19866e4d02bb6043590dd433873fc38d3650b8def21e647654a ] || fail 'Signal payload hash mismatch'
# Validate BOTH firmware components before mounting either patch.
case "$(hash /lib64/libvendor-ril.so)" in 247c82094dfdb47756aedce77a99fe06cf95b1941cfdd315713e408756306d85|1ec44b63fd7dd2a5d0cc88a06fe82c802f2ec415aff87861611b6ed95c6ef201) ;; *) fail 'Unsupported radio firmware';; esac
case "$(hash /mnt/hirain_app/usr/bin/hrild)" in 25b8787e5bfe471abb4329c4128e942dab89544ffab4b7aca3e8901b105ed721|0396fb7a3d1cf19866e4d02bb6043590dd433873fc38d3650b8def21e647654a) ;; *) fail 'Unsupported signal firmware';; esac
if [ "$(hash /lib64/libvendor-ril.so)" = 247c82094dfdb47756aedce77a99fe06cf95b1941cfdd315713e408756306d85 ]; then
 mount --bind "$BASE/libvendor-ril-test.so" /lib64/libvendor-ril.so
 touch "$BASE/radio-mounted"
 systemctl restart ql-rild.service
fi
if [ "$(hash /mnt/hirain_app/usr/bin/hrild)" = 25b8787e5bfe471abb4329c4128e942dab89544ffab4b7aca3e8901b105ed721 ]; then
 mount --bind "$BASE/hrild-signal" /mnt/hirain_app/usr/bin/hrild
 touch "$BASE/signal-mounted"
 pid=$(pidof hrild || true); [ -z "$pid" ] || kill -TERM $pid
fi
wait_at || fail 'Modem service unavailable'
at 'AT+CFUN=1' || fail 'Radio activation rejected'
echo 'DONE: Radio and signal patches'
# Read SIM identity, not the roaming/host operator. Never log IMSI.
n=0
until at 'AT+CPIN?' && printf '%s\n' "$reply" | grep -q 'READY'; do
 n=$((n+1)); [ "$n" -lt 12 ] || fail 'SIM is absent, locked or not ready'; sleep 2
done
at 'AT+CIMI' || fail 'Cannot identify SIM operator'
plmn=$(printf '%s\n' "$reply" | awk '/^[[:space:]]*[0-9]{14,16}[[:space:]]*$/{gsub(/[[:space:]]/,"");print substr($0,1,5);exit}')
# BusyBox awk versions without interval expressions.
[ -n "$plmn" ] || plmn=$(printf '%s\n' "$reply" | tr -d '\r' | awk '{gsub(/^[ \t]+|[ \t]+$/,"");if($0~/^[0-9]+$/&&length($0)>=14&&length($0)<=16){print substr($0,1,5);exit}}')
[ -n "$plmn" ] || fail 'Invalid SIM identity response'
apn=$(cat "$BASE/apn-override")
[ -n "$apn" ] || apn=$(awk -v p="$plmn" '$1==p{print $2;exit}' "$BASE/apns.tsv")
# Unknown carrier: empty APN requests network-selected default. No random carrier fallback.
case "$apn" in *[!A-Za-z0-9._-]*) fail 'Invalid APN';; esac
n=0
until at 'AT+CEREG?' && printf '%s\n' "$reply" | grep -Eq '\+CEREG: *[0-9]+, *(1|5)([^0-9]|$)'; do
 n=$((n+1)); [ "$n" -lt 18 ] || fail 'Network registration timed out'; sleep 3
done
at 'AT+CGDCONT?' || fail 'Cannot read PDP contexts'
contexts=$reply
# Reuse only a context owned by this installer and still carrying its last APN.
cid=''
if [ -f "$BASE/owned-context" ]; then
 oldcid=$(cut -d '|' -f1 "$BASE/owned-context"); oldapn=$(cut -d '|' -f2 "$BASE/owned-context")
 found=$(printf '%s\n' "$contexts" | awk -F, -v c="$oldcid" '$1~("\\+CGDCONT: *"c"$"){gsub(/[" ]/,"",$3);print $3;exit}')
 if [ "$found" = "$oldapn" ] && printf '%s\n' "$contexts" | grep -Eq "\\+CGDCONT: *$oldcid,"; then cid=$oldcid; fi
fi
if [ -z "$cid" ]; then
 for candidate in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16; do
  if ! printf '%s\n' "$contexts" | grep -Eq "\\+CGDCONT: *$candidate,"; then cid=$candidate; break; fi
 done
fi
[ -n "$cid" ] || fail 'No unused PDP context; factory contexts were preserved'
# Journal ownership before changing the modem, enabling recovery after disconnection.
printf '%s|%s\n' "$cid" "$apn" > "$BASE/owned-context.new"
mv "$BASE/owned-context.new" "$BASE/owned-context"
at "AT+CGACT=0,$cid" || true
at "AT+CGDCONT=$cid,\"IP\",\"$apn\"" || fail 'APN configuration rejected'
uplink=''; n=0
while [ -z "$uplink" ]; do
 at "AT+CGACT=1,$cid" || true
 if at "AT+CGPADDR=$cid"; then
  addr=$(printf '%s\n' "$reply" | awk -F, -v c="$cid" '$1~("\\+CGPADDR: *"c"$"){gsub(/[" \r]/,"",$2);print $2;exit}')
  if valid_ip "$addr"; then
   uplink=$(ip -o -4 addr show | awk -v addr="$addr" '$2~/^ccmni[0-9]+$/{split($4,a,"/");if(a[1]==addr){print $2;exit}}')
  fi
 fi
 n=$((n+1)); [ "$n" -lt 18 ] || fail 'Mobile data activation timed out'; [ -n "$uplink" ] || sleep 3
 done
printf 'DONE: Mobile data (%s)\n' "${apn:-network-default}"
# Obtain DNS negotiated for THIS context, never hardcode another carrier's private DNS.
dns=$(cat "$BASE/dns-override")
if [ -z "$dns" ] && at "AT+CGCONTRDP=$cid"; then
 dns=$(printf '%s\n' "$reply" | awk -F, -v c="$cid" '$1~("\\+CGCONTRDP: *"c"$"){gsub(/[" ]/,"",$6);print $6;exit}')
fi
if ! valid_ip "$dns"; then
 dns=$(awk '$1=="nameserver"&&$2!~/^(127\.|0\.)/{print $2;exit}' /tmp/ql_lan_resolv.conf 2>/dev/null || true)
fi
valid_ip "$dns" || fail 'No usable carrier DNS; set payload/dns-override and retry'
remove_network
# Never overwrite existing default routes. Lower priority than factory routes.
ip route add default dev "$uplink" metric 709
printf '%s\n' "$uplink" > "$BASE/route-added"
[ -f "$BASE/forward-before" ] || cat /proc/sys/net/ipv4/ip_forward > "$BASE/forward-before"
echo 1 > /proc/sys/net/ipv4/ip_forward
iptables -t nat -N VI_KIT_OUT
iptables -t nat -N VI_KIT_DNS
for source in 172.16.104.40 172.16.110.40 172.16.120.40; do
 iptables -t nat -A VI_KIT_OUT -s "$source/32" -o "$uplink" -j MASQUERADE
done
iptables -t nat -A POSTROUTING -j VI_KIT_OUT
for dst in 180.76.76.76 223.5.5.5 114.114.114.114; do
 for proto in udp tcp; do
  iptables -t nat -A VI_KIT_DNS -s 172.16.104.40/32 -d "$dst" -p "$proto" --dport 53 -j DNAT --to-destination "$dns:53"
 done
done
iptables -t nat -A PREROUTING -i eth2 -j VI_KIT_DNS
# Verify real upstream data using the selected interface and negotiated DNS.
probe=$(cat "$BASE/https-probe")
case "$probe" in https://*) ;; *) fail 'HTTPS probe must use TLS';; esac
# Resolve through the carrier DNS without modifying factory resolver files.
host=$(printf '%s\n' "$probe" | sed 's|https://||;s|/.*||')
nslookup "$host" "$dns" >/tmp/vi-kit-dns-check 2>&1 || fail 'Carrier DNS lookup failed'
resolved=$(awk '/^Name:/{answer=1} answer && /^Address([[:space:]][0-9]+)?:/{for(i=2;i<=NF;i++){a=$i;sub(/#.*/,"",a);if(a~/^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$/){print a;exit}}}' /tmp/vi-kit-dns-check)
valid_ip "$resolved" || fail 'DNS response did not contain IPv4'
code=$(curl --interface "$uplink" --resolve "$host:443:$resolved" --connect-timeout 10 --max-time 25 -sS -o /dev/null -w '%{http_code}' "$probe") || fail 'Mobile HTTPS check failed'
case "$code" in 2??|3??) ;; *) fail "Mobile HTTPS returned $code";; esac
echo 'DONE: T-box Internet verified; headunit routing configured'
# Refresh factory SignalManage after PDP becomes ready; retain factory notifications.
pid=$(pidof hrild || true); [ -z "$pid" ] || kill -TERM $pid
sleep 3
wait_at || fail 'Signal service did not recover'
busctl --address=unix:path=/tmp/.hirain-dbus --timeout=8 introspect gdbus.hcommon.rild /gdbus/hcommon/rild gdbus.hcommon.rild.signal >/tmp/vi-kit-signal-check 2>&1 || fail 'Factory signal interface unavailable'
echo 'DONE: Factory signal reporting service'
printf '%s\n' "$uplink $cid $dns" > "$BASE/ready"
