#!/usr/bin/env python3
"""Refresh the bundled monster and equipment data.

Downloads the OSRS Wiki DPS calculator dataset (one request per file) and
writes trimmed copies into src/main/resources. Run it by hand when the game
gets new monsters or gear, then rebuild the plugin.

Data: OSRS Wiki contributors, CC BY-NC-SA 3.0 (https://github.com/weirdgloop/osrs-dps-calc)

Usage: scripts/update_data.py [local-dir-with-monsters.json-and-equipment.json]
"""
import json
import pathlib
import sys
import urllib.request

BASE = "https://raw.githubusercontent.com/weirdgloop/osrs-dps-calc/main/cdn/json/"
OUT = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/com/gearoptimizer/data"
DROP = {"image", "weight", "immunities"}


def load(name, local):
	if local:
		return json.loads((pathlib.Path(local) / name).read_text())
	with urllib.request.urlopen(BASE + name) as resp:
		return json.load(resp)


def main():
	local = sys.argv[1] if len(sys.argv) > 1 else None
	OUT.mkdir(parents=True, exist_ok=True)
	for name in ("monsters.json", "equipment.json"):
		rows = [{k: v for k, v in row.items() if k not in DROP} for row in load(name, local)]
		(OUT / name).write_text(json.dumps(rows, separators=(",", ":")))
		print(f"{name}: {len(rows)} rows")


if __name__ == "__main__":
	main()
