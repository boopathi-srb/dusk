#!/bin/bash
# Installs, switches on/off, sets up and removes Dusk on a Fire TV over adb.
# Usage: TV=<tv-ip>:5555 ./tv.sh <action> [arguments]
#   install | on | off | uninstall | logs | shot | clear-continue
#   name <your name> | auto      e.g. ./tv.sh name Sam  (default: the owner in "Sam's Fire TV")
#   city <lat> <lon> | auto      e.g. ./tv.sh city 51.51 -0.13  (default: found from the TV's postcode)
# Note: on some Fire TVs ADB debugging switches itself off after a restart; turn it back on first.

ACTION="$1"
TV_ADDRESS="${TV:-}"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
APK="$PROJECT_DIR/build/tvhome.apk"
PACKAGE="local.tvhome"
SERVICE="$PACKAGE/$PACKAGE.HomeTakeoverService"
MEDIA_WATCHER="$PACKAGE/$PACKAGE.MediaWatcher"

fail() {
    echo "tv.sh $ACTION failed: $1" >&2
    exit 1
}

tv() {
    adb -s "$TV_ADDRESS" "$@"
}

connect() {
    [ -n "$TV_ADDRESS" ] || fail "set the TV's address first, e.g. TV=192.168.1.20:5555 ./tv.sh $ACTION"
    adb connect "$TV_ADDRESS" >/dev/null 2>&1
    [ "$(tv get-state 2>/dev/null | tr -d '\r')" = "device" ] \
        || fail "TV at $TV_ADDRESS not reachable over adb (is ADB debugging on?)"
}

current_services() {
    tv shell settings get secure enabled_accessibility_services | tr -d '\r'
}

switch_on() {
    tv shell pm grant "$PACKAGE" android.permission.READ_LOGS || fail "grant READ_LOGS"
    tv shell pm grant "$PACKAGE" android.permission.READ_TV_LISTINGS || fail "grant READ_TV_LISTINGS"
    tv shell appops set "$PACKAGE" WRITE_SETTINGS allow || fail "allow brightness changes (WRITE_SETTINGS)"
    tv shell pm grant "$PACKAGE" android.permission.WRITE_SECURE_SETTINGS || fail "grant ADB switch (WRITE_SECURE_SETTINGS)"
    tv shell pm grant "$PACKAGE" android.permission.ACCESS_FINE_LOCATION || fail "grant Wi-Fi network name (location)"
    local services
    services="$(current_services)"
    if [ -z "$services" ] || [ "$services" = "null" ]; then
        services="$SERVICE"
    elif [[ ":$services:" != *":$SERVICE:"* ]]; then
        services="$services:$SERVICE"
    fi
    tv shell settings put secure enabled_accessibility_services "$services" || fail "enable Home service"
    tv shell settings put secure accessibility_enabled 1 || fail "enable accessibility"
    tv shell cmd notification allow_listener "$MEDIA_WATCHER" || fail "allow Continue watching listener"
    echo "Home button now opens the home screen."
}

switch_off() {
    local remaining
    remaining="$(current_services | tr ':' '\n' | grep -v -x "$SERVICE" | paste -sd ':' -)"
    if [ -z "$remaining" ] || [ "$remaining" = "null" ]; then
        tv shell settings delete secure enabled_accessibility_services >/dev/null || fail "disable Home service"
        tv shell settings put secure accessibility_enabled 0 || fail "disable accessibility"
    else
        tv shell settings put secure enabled_accessibility_services "$remaining" || fail "disable Home service"
    fi
    tv shell cmd notification disallow_listener "$MEDIA_WATCHER" >/dev/null 2>&1
    echo "Home button is back to Amazon's home."
}

connect
case "$ACTION" in
    install)
        [ -f "$APK" ] || fail "no APK at $APK; run ./build.sh first"
        tv install -r "$APK" || fail "install"
        switch_on
        ;;
    on) switch_on ;;
    off) switch_off ;;
    uninstall)
        switch_off
        tv uninstall "$PACKAGE" || fail "uninstall"
        ;;
    logs) tv logcat -v time -s TvHome:* ;;
    clear-continue)
        # Empties Continue watching: YouTube history cleared, the apps' current cards hidden in Dusk.
        tv shell am start -n "$PACKAGE/.HomeActivity" --ez local.tvhome.CLEAR_CONTINUE true >/dev/null \
            || fail "clear Continue watching"
        echo "Continue watching cleared; newly watched shows will appear."
        ;;
    shot)
        file="$PROJECT_DIR/build/screen-$(date +%H%M%S).png"
        tv exec-out screencap -p > "$file" || fail "screenshot"
        echo "$file"
        ;;
    name)
        [ -n "$2" ] || fail "give a name, e.g. ./tv.sh name Sam (or: ./tv.sh name auto)"
        given="$2"
        [ "$given" = "auto" ] && given=""
        tv shell am start -n "$PACKAGE/.HomeActivity" --es local.tvhome.SET_NAME "'$given'" >/dev/null || fail "set name"
        if [ -z "$given" ]; then echo "Greeting uses the name from the TV's own name."; else echo "Greeting set for $given."; fi
        ;;
    city)
        if [ "$2" = "auto" ]; then
            tv shell am start -n "$PACKAGE/.HomeActivity" --es local.tvhome.SET_LATITUDE auto >/dev/null || fail "set city"
            echo "City will be found from the TV's postcode."
        else
            [ -n "$2" ] && [ -n "$3" ] || fail "give latitude and longitude, e.g. ./tv.sh city 51.51 -0.13 (or: ./tv.sh city auto)"
            tv shell am start -n "$PACKAGE/.HomeActivity" --es local.tvhome.SET_LATITUDE "$2" \
                --es local.tvhome.SET_LONGITUDE "$3" >/dev/null || fail "set city"
            echo "City set; weather will update shortly."
        fi
        ;;
    *) fail "unknown action; use install, on, off, uninstall, logs, shot, clear-continue, name or city" ;;
esac
