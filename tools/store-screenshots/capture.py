"""Capture the real app, using only the opt-in emulator screenshot package."""
import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PACKAGE = "app.wend.sea.screenshots"

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--device", required=True)
    parser.add_argument("--scale", type=int, choices=[1, 2], default=2, help="2x capture keeps the enlarged globe sharp")
    parser.add_argument("--locales", nargs="+", default=["ko-KR", "en-US"])
    args = parser.parse_args()
    if not args.device.startswith("emulator-"):
        parser.error("Only emulator-* devices are allowed. Physical devices are never modified.")
    def adb(*cmd):
        return subprocess.check_output(["adb", "-s", args.device, *cmd])
    def shell(*cmd):
        return adb("shell", *cmd).decode()
    def tap_text(text):
        for attempt in range(8):
            shell("uiautomator", "dump", "/sdcard/wend-store-layout.xml")
            xml = shell("cat", "/sdcard/wend-store-layout.xml")
            for node in ET.fromstring(xml).iter("node"):
                if node.get("text") == text or node.get("content-desc") == text:
                    x1,y1,x2,y2 = map(int, re.findall(r"\d+", node.get("bounds")))
                    shell("input", "tap", str((x1+x2)//2), str((y1+y2)//2))
                    time.sleep(2)
                    return
            time.sleep(1)
        raise RuntimeError("UI target missing: " + text + "\n" + xml)
    original_size = shell("wm", "size")
    original_density = shell("wm", "density")
    def restore(kind, original):
        match = re.search(r"Override (?:size|density): (\S+)", original)
        shell("wm", kind, match[1] if match else "reset")
    demo_allowed = shell("settings", "get", "global", "sysui_demo_allowed").strip()
    try:
        shell("settings", "put", "global", "sysui_demo_allowed", "1")
        for values in [("command", "enter"), ("command", "clock", "hhmm", "0941"), ("command", "notifications", "visible", "false"), ("command", "battery", "level", "100", "plugged", "false"), ("command", "network", "wifi", "show", "mobile", "show", "sims", "1", "nosim", "hide", "level", "4", "datatype", "lte", "fully", "true")]:
            extras = [v for pair in zip(values[::2], values[1::2]) for v in ("--es", *pair)]
            shell("am", "broadcast", "-a", "com.android.systemui.demo", *extras)
        shell("wm", "size", f"{1080*args.scale}x{1920*args.scale}")
        shell("wm", "density", str(360*args.scale))
        for locale in args.locales:
            copy = json.loads((ROOT / "locales" / (locale + ".json")).read_text())
            shell("am", "force-stop", PACKAGE)
            shell("cmd", "locale", "set-app-locales", PACKAGE, "--locales", locale)
            result = shell("am", "instrument", "-w", "-e", "class", "app.wend.sea.StoreFixtureTest", "-e", "locale", locale,
                           PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner")
            if "OK (1 test)" not in result:
                raise RuntimeError(result)
            shell("am", "start", "-n", PACKAGE + "/app.wend.sea.MainActivity")
            time.sleep(8)
            out = ROOT / "raw" / locale
            out.mkdir(parents=True, exist_ok=True)
            def capture(name):
                time.sleep(2)
                (out / (name + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))
                print(locale, name, flush=True)
            capture("01-rediscover")
            tap_text(copy["fixtureTrip"])
            capture("02-relive")
            tap_text(copy["bestButton"])
            capture("03-best-shots")
            shell("input", "keyevent", "KEYCODE_BACK")
            time.sleep(2)
            tap_text(copy["cleanButton"])
            capture("04-clean-up")
    finally:
        shell("am", "broadcast", "-a", "com.android.systemui.demo", "--es", "command", "exit")
        if demo_allowed == "null":
            shell("settings", "delete", "global", "sysui_demo_allowed")
        else:
            shell("settings", "put", "global", "sysui_demo_allowed", demo_allowed)
        restore("size", original_size)
        restore("density", original_density)

if __name__ == "__main__":
    main()
