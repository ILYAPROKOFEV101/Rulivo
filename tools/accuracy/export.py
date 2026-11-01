#!/usr/bin/env python3
"""Read only Rulivo's private diagnostic files from a specified debug device (no camera images)."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
from datetime import datetime, timezone

from report import read_sessions


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True, help="Explicit device from adb devices -l")
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    def adb(*command):
        return subprocess.check_output([args.adb, "-s", args.serial, *command], timeout=30)
    try:
        names = adb("exec-out", "run-as", "com.ilya.rulivo", "ls", "no_backup/accuracy").decode().splitlines()
        names = [name for name in names if re.fullmatch(r"accuracy-[a-f0-9-]+\.jsonl", name)]
        if not names:
            parser.error("No diagnostics: start a debug build with --ez accuracy_diagnostics true, then pause before exporting")
        args.out.mkdir(parents=True, exist_ok=True)
        hashes = {}
        for name in names:
            content = adb("exec-out", "run-as", "com.ilya.rulivo", "cat", "no_backup/accuracy/" + name)
            (args.out / name).write_bytes(content)
            hashes[name] = hashlib.sha256(content).hexdigest()
        package_paths = adb("shell", "pm", "path", "com.ilya.rulivo").decode().splitlines()
        apk_hashes = []
        for package_path in package_paths:
            if package_path.startswith("package:"):
                apk_path = package_path[len("package:"):]
                result = adb("shell", "sha256sum", shlex.quote(apk_path)).decode().strip()
                apk_hashes.append(result)
        manifest = {"exported_at_utc": datetime.now(timezone.utc).isoformat(),
                    "logs_sha256": hashes, "installed_apks_sha256": apk_hashes,
                    "note": "Export only; no claim of physical accuracy or complete trial coverage"}
        (args.out / "export.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        sessions = read_sessions(args.out)
        inventory = []
        for sid, session in sessions.items():
            completed = []
            for frame in session.frames:
                command = frame.get("command") or {}
                if command.get("outcome") == "COMPLETED":
                    mid = command["completed_measurement_id"]
                    item = next(m for m in frame["measurements"] if m["id"] == mid)
                    completed.append({"measurement_id": mid, "distance_m": item["distance_m"],
                                      "wall_time_ms": frame["wall_time_ms"]})
            inventory.append({"session_id": sid, "model": session.metadata["model"],
                              "algorithm": session.metadata["algorithm"], "depth_mode": session.metadata["depth_mode"],
                              "completed_measurements": completed, "warnings": session.warnings})
        (args.out / "inventory.json").write_text(json.dumps(inventory, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(inventory, ensure_ascii=False, indent=2))
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired, ValueError, OSError, KeyError) as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
