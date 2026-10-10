#!/system/bin/sh
# Experimental local-only profile. No cloud token, VIN, NVRAM or CAN writes.
set -eu
step=initializing
report_exit() {
    result=$?
    trap - EXIT
    echo "LOCAL_PROFILE_RESULT step=$step exit=$result"
    exit "$result"
}
trap report_exit EXIT
[ "$(id -u)" = 0 ] || { echo 'Root required'; exit 1; }
base=/data/local/voyahiran-local-profile
account=/private/configs/token/accountInfo
fake=/tmp/fake_login
operation=${1:-status}
case "$operation" in status|apply|restore|watch) ;; *) exit 2;; esac
status() {
    [ -f "$base/enabled" ] && echo 'boot_apply=enabled' || echo 'boot_apply=disabled'
    [ -e "$fake" ] && echo 'activation_override=present' || echo 'activation_override=absent'
    [ "$(getprop ignoreActivation)" = true ] && echo 'login_bypass=enabled' || echo 'login_bypass=disabled'
    if [ ! -f "$account" ]; then echo 'profile=missing'
    elif [ -f "$base/profile" ] && cmp -s "$account" "$base/profile"; then echo 'profile=local_test'
    elif [ "$(cat "$account")" = guest ]; then echo 'profile=guest'
    else echo 'profile=other_preserved'; fi
}
lock() {
    mkdir /data/local/tmp/voyahiran-local-profile.lock 2>/dev/null || { echo 'Another profile operation is running'; exit 1; }
    trap 'result=$?; rmdir /data/local/tmp/voyahiran-local-profile.lock || :; echo "LOCAL_PROFILE_RESULT step=$step exit=$result"; exit "$result"' EXIT
}
restore() (
    step=lock
    lock
    rm -f "$base/enabled"
    [ -f "$base/ready" ] || { echo 'No completed backup'; exit 1; }
    if [ -f "$account" ] && ! cmp -s "$account" "$base/profile" && [ "$(cat "$account")" != guest ]; then
        echo 'Different account detected; refusing to overwrite it'; exit 1
    fi
    if [ -f "$base/account.original" ]; then
        cp -p "$base/account.original" "$account.voyah-restore"
        mv -f "$account.voyah-restore" "$account"
    else rm -f "$account"; fi
    if [ ! -f "$base/fake.existed" ] && [ -e "$fake" ]; then rm -f "$fake"; fi
    for prop in ignoreActivation com.qinggan.user_started; do
        setprop "$prop" "$(cat "$base/$prop.original")"
    done
    rm -f "$base/watch"
    echo 'LOCAL_PROFILE_RESTORED'
)
apply() (
    step=lock
    lock
    step=check_account_directory
    [ -d /private/configs/token ] || { echo 'OEM account directory missing'; exit 1; }
    [ ! -L "$account" ] || { echo 'Unexpected account symlink'; exit 1; }
    if [ -f "$account" ] && [ "$(cat "$account")" != guest ]; then
        if [ ! -f "$base/profile" ] || ! cmp -s "$account" "$base/profile"; then
            echo 'Existing non-guest account preserved'; return 3
        fi
    fi
    if [ -d "$base" ] && [ ! -f "$base/ready" ]; then
        echo 'Incomplete previous backup; refusing to overwrite it'; exit 1
    fi
    step=backup
    mkdir -p "$base"; chmod 700 "$base"
    if [ ! -f "$base/ready" ]; then
        [ ! -f "$account" ] || cp -p "$account" "$base/account.original"
        [ ! -e "$fake" ] || touch "$base/fake.existed"
        for prop in ignoreActivation com.qinggan.user_started; do getprop "$prop" > "$base/$prop.original"; done
        if [ ! -f "$base/profile" ]; then
            identity=$(cat /proc/sys/kernel/random/uuid | tr -d '-')
            [ ${#identity} = 32 ] || exit 1
            printf 'Voyah Iran%%0%%0%%%s%%100001' "$identity" > "$base/profile"
        fi
        touch "$base/ready"
    fi
    step=create_login_marker
    # Some production images have a read-only root and no /tmp. The OEM
    # ignoreActivation path can still be tested; never remount the system here.
    if [ ! -e "$fake" ]; then
        if mkdir -p /tmp && touch "$fake" && chmod 644 "$fake"; then
            echo 'LOCAL_PROFILE_MARKER_CREATED'
        else
            echo 'LOCAL_PROFILE_MARKER_UNAVAILABLE'
        fi
    fi
    step=write_profile
    if ! cmp -s "$account" "$base/profile"; then
        # Copy original metadata when present; atomic replacement prevents partial reads.
        if [ -f "$account" ]; then cp -p "$account" "$account.voyah-new"; else touch "$account.voyah-new"; chmod 644 "$account.voyah-new"; fi
        cat "$base/profile" > "$account.voyah-new"
        mv -f "$account.voyah-new" "$account"
        echo 'LOCAL_PROFILE_WRITTEN'
    fi
    step=set_ignore_activation
    [ "$(getprop ignoreActivation)" = true ] || setprop ignoreActivation true
    step=close_login_view
    [ "$(getprop com.qinggan.user_started)" = closeLoginView ] || setprop com.qinggan.user_started closeLoginView
    step=verify
    cmp -s "$account" "$base/profile" || { echo 'LOCAL_PROFILE_VERIFY_FAILED account'; exit 1; }
    [ "$(getprop ignoreActivation)" = true ] || { echo 'LOCAL_PROFILE_VERIFY_FAILED ignoreActivation'; exit 1; }
    [ "$(getprop com.qinggan.user_started)" = closeLoginView ] || { echo 'LOCAL_PROFILE_VERIFY_FAILED user_started'; exit 1; }
    touch "$base/enabled"
    echo 'LOCAL_PROFILE_SETTINGS_VERIFIED'
)
case "$operation" in
status) status;;
apply) apply; status;;
restore) restore; status;;
watch)
    apply
    touch "$base/watch"
    echo 'Monitoring every 10 seconds; restore or remove watch file to stop'
    while [ -f "$base/watch" ]; do sleep 10; [ ! -f "$base/watch" ] || apply || exit $?; done
    ;;
esac
