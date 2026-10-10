#!/usr/bin/env python3
"""Optional integration checks on the separately installed debug preview only.

Uses a connected, unlocked Android phone. Temporarily replaces the PREVIEW save,
then restores it. Never accesses the production app or changes the phone clock.
Run after ./build.sh preview install: python3 tests/device_smoke.py
"""
import json
import os
from pathlib import Path
import subprocess
import time

ADB = os.environ.get("ADB", str(Path.home() / "Android/Sdk/platform-tools/adb"))
PACKAGE = "com.wesley.flippet.preview"
ACTIVITY = PACKAGE + "/com.wesley.flippet.MainActivity"


def adb(*args, data=None):
    return subprocess.check_output([ADB, *args], input=data, stderr=subprocess.STDOUT)


def stop():
    adb("shell", "am", "force-stop", PACKAGE)


def read():
    return json.loads(adb("shell", "run-as", PACKAGE, "cat", "files/pet.json"))


def write(state):
    stop()
    adb("shell", "run-as", PACKAGE, "sh", "-c", "'cat > files/pet.json'",
        data=json.dumps(state).encode())


def launch():
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)


def key(code):
    adb("shell", "input", "keyevent", code)


def seed(elapsed=0, **changes):
    now = int(time.time())
    state = dict(name="Test", born=now-elapsed, last=now-elapsed,
                 fed=70, joy=70, vitality=80, wards=75, asleep=False,
                 sick=False, dead=False, dead_at=0, survival=False,
                 cause="", neglect=0, generation=3, phenotype=4)
    state.update(changes)
    write(state)
    launch()
    key("KEYCODE_BACK")  # onPause flushes the updated state.
    return read()


stop()
original = read()
checks = 0


def check(condition, label):
    global checks
    assert condition, label
    checks += 1
    print("PASS", label)


try:
    p = seed(7*86400)
    check(not p["dead"] and p["fed"] == 0 and p["neglect"] == 0,
          "Gentle survives a week unattended")
    p = seed(5*3600+60, survival=True)
    check(not p["dead"] and 60 <= p["neglect"] < 90,
          "Survival counts only time after the meter empties")
    p = seed(5*3600+901, survival=True)
    check(p["dead"] and p["dead_at"] - p["born"] == 18900,
          "Survival records death at five hours fifteen minutes")
    check("starved" in p["cause"], "Death cause matches first empty meter")

    old = dict(original)
    old.pop("survival", None)
    old.pop("dead_at", None)
    old.update(name="Old friend", dead=False, last=int(time.time()), generation=7)
    write(old)
    launch()
    key("KEYCODE_BACK")
    p = read()
    check(not p["survival"] and p["name"] == "Old friend" and p["generation"] == 7,
          "Old saves migrate to Gentle without replacing the pet")

    seed(fed=100, wards=100)
    key("KEYCODE_BACK")  # return from menu
    key("KEYCODE_1")
    check(read()["sick"], "Overfeeding remains sick even at full salt")
    key("KEYCODE_3")
    check(not read()["sick"], "Fresh Ward action cures sickness")
    key("KEYCODE_4")
    check(read()["asleep"], "Key 4 sleeps")
    key("KEYCODE_4")
    check(not read()["asleep"], "Key 4 wakes")
    key("KEYCODE_2")
    check(read()["vitality"] < 69, "Key 2 plays and spends vitality")
    stop()
    launch()
    key("KEYCODE_BACK")
    check(read()["vitality"] < 69, "Actions persist across process restart")

    now = int(time.time())
    p = seed(born=now-3600, last=now+3600)
    check(abs((p["last"]-p["born"])-7200) < 10 and p["fed"] > 69,
          "Backward clock correction preserves age without charging decay")

    p = seed(3600, dead=True, dead_at=int(time.time())-1800, cause="old death")
    check(p["dead"] and p["cause"] == "old death", "Gentle does not revive a dead pet")
    print(str(checks) + " device checks passed")
finally:
    write(original)
    launch()
