#!/usr/bin/env python3
"""Refresh the bundled monster locations.

Reads the location tables ({{LocLine}}) of every monster page on the OSRS Wiki
(50 pages per request) and writes, for each monster id in monsters.json, the
places it can be found with their spawn coordinates, and whether each is in the
Wilderness (the wiki's league region), where blighted supplies work. Places off the surface map
(dungeons, caves, other realms) also get their surface entrances: the ladder,
stairs or cave from Shortest Path's transport table that lands right by the
monsters, and the map markers on the place's own wiki page. Run it after
update_data.py, then rebuild the plugin. The plugin itself never contacts the
wiki: this file is bundled with it.

Data: OSRS Wiki contributors, CC BY-NC-SA 3.0
Transports: Shortest Path plugin (https://github.com/Skretzo/shortest-path), BSD 2-Clause

Usage: scripts/update_locations.py
"""
import json
import pathlib
import re
import time
import urllib.parse
import urllib.request

API = "https://oldschool.runescape.wiki/api.php"
USER_AGENT = "gear-optimizer-data-script (https://github.com/NigmaX/runelite-plugin-gear-optimizer)"
DATA = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/com/gearoptimizer/data"
TRANSPORTS = "https://raw.githubusercontent.com/Skretzo/shortest-path/master/src/main/resources/transports/transports.tsv"
BATCH = 50
# The surface map spans these y values; outside them are dungeons, caves, instances and other separate maps
SURFACE_Y = (2400, 4160)
MAX_ENTRANCES = 4
# Entrances closer together than this are one entrance
SAME_ENTRANCE = 8
# How close a transport must land to the monsters to count as their entrance; further out it may be another dungeon's
LANDING_RANGE = 20

# "x:1608,y:9994" or "2993,4254", optionally with a plane
COORDINATE = re.compile(r"(?:x:)?\s*(\d+)\s*,\s*(?:y:)?\s*(\d+)(?:\s*,\s*(?:plane:|p:)?\s*(\d+))?")
ORDINALS = {0: "ground floor", 1: "1st floor", 2: "2nd floor", 3: "3rd floor"}


def fetch(url):
	request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
	return urllib.request.urlopen(request)


def fetch_pages(titles):
	"""@return {requested title: wikitext} for the pages that exist"""
	params = {
		"action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main",
		"format": "json", "formatversion": "2", "redirects": "1", "titles": "|".join(titles),
	}
	with fetch(API + "?" + urllib.parse.urlencode(params)) as resp:
		query = json.load(resp)["query"]
	# Follow the title back through normalisation and redirects to the name we asked for
	renamed = {}
	for step in query.get("normalized", []) + query.get("redirects", []):
		renamed[step["to"]] = renamed.get(step["from"], step["from"])
	pages = {}
	for page in query["pages"]:
		if "revisions" in page:
			text = page["revisions"][0]["slots"]["main"]["content"]
			pages[renamed.get(page["title"], page["title"])] = text
			pages[page["title"]] = text
	return pages


def templates(text, name):
	"""@return the bodies of every {{name ...}} in the text, nested templates included"""
	bodies = []
	start = 0
	while True:
		start = text.find("{{" + name, start)
		if start < 0:
			return bodies
		after = text[start + 2 + len(name):start + 3 + len(name)]
		if after and after not in "|\n }":
			start += 2
			continue
		depth, i = 0, start
		while i < len(text):
			if text.startswith("{{", i):
				depth += 1
				i += 2
			elif text.startswith("}}", i):
				depth -= 1
				i += 2
				if depth == 0:
					break
			else:
				i += 1
		bodies.append(text[start + 2 + len(name):i - 2])
		start = i


def split_params(body):
	"""Splits a template body on the pipes that aren't inside a nested template or link."""
	params, depth, current = [], 0, []
	i = 0
	while i < len(body):
		pair = body[i:i + 2]
		if pair in ("{{", "[["):
			depth += 1
			current.append(pair)
			i += 2
		elif pair in ("}}", "]]"):
			depth -= 1
			current.append(pair)
			i += 2
		elif body[i] == "|" and depth == 0:
			params.append("".join(current))
			current = []
			i += 1
		else:
			current.append(body[i])
			i += 1
	params.append("".join(current))
	return [p.strip() for p in params if p.strip()]


def plain(wikitext):
	"""Turns a location cell into readable text."""
	text = re.sub(r"<ref[^>]*/>|<ref.*?</ref>", "", wikitext, flags=re.S)
	text = re.sub(r"\{\{\s*FloorNumber\s*\|[^}]*?uk\s*=\s*(-?\d+)[^}]*\}\}",
		lambda m: ORDINALS.get(int(m.group(1)), m.group(1) + "th floor"), text)
	text = re.sub(r"\{\{\s*Fairycode\s*\|\s*([a-zA-Z]{3})\s*\}\}", lambda m: "fairy ring " + m.group(1).upper(), text)
	while "{{" in text:
		replaced = re.sub(r"\{\{[^{}]*\}\}", "", text)
		if replaced == text:
			break
		text = replaced
	text = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", text)
	text = re.sub(r"<[^>]+>|'''?", "", text)
	text = re.sub(r"\(\s*\)", "", text)
	return re.sub(r"\s+", " ", text).strip(" ,-")


def parse_lines(text):
	lines = []
	for body in templates(text, "LocLine"):
		named, points = {}, []
		for param in split_params(body):
			coordinate = COORDINATE.match(param)
			if coordinate and "=" not in param:
				points.append(coordinate)
				continue
			key, sep, value = param.partition("=")
			if sep:
				named[key.strip().lower()] = value.strip()
		location = plain(named.get("location", ""))
		if not location:
			continue
		try:
			plane = int(named.get("plane", "0") or 0)
		except ValueError:
			plane = 0
		levels = set(re.findall(r"\d+", named.get("levels", "")))
		coords = []
		seen = set()
		for point in points:
			x, y, p = int(point.group(1)), int(point.group(2)), int(point.group(3) or plane)
			if (x, y, p) not in seen:
				seen.add((x, y, p))
				coords.append((x, y, p))
		page = link_target(named.get("location", ""))
		wilderness = "wilderness" in named.get("leagueregion", "").lower()
		lines.append({"location": location, "name": named.get("name", ""), "page": page, "levels": levels, "coords": coords,
			"wilderness": wilderness})
	return lines or infobox_line(text)


def link_target(wikitext):
	"""@return the page the first link in the text points to, or ''"""
	match = re.search(r"\[\[([^|\]#]+)", wikitext)
	return match.group(1).strip() if match else ""


def map_points(text):
	"""@return every point marked by the page's {{Map}} templates, in order"""
	coords = []
	for body in templates(text, "Map"):
		params = split_params(body)
		named = {k.strip().lower(): v.strip() for k, _, v in (p.partition("=") for p in params if "=" in p)}
		plane = int(named["plane"]) if named.get("plane", "").isdigit() else 0
		if named.get("x", "").isdigit() and named.get("y", "").isdigit():
			coords.append((int(named["x"]), int(named["y"]), plane))
		for param in params:
			coordinate = COORDINATE.match(param)
			if coordinate and "=" not in param:
				coords.append((int(coordinate.group(1)), int(coordinate.group(2)), int(coordinate.group(3) or plane)))
	return coords


def infobox_line(text):
	"""For pages without a location table: the infobox location, placed at the page's {{Map}} markers."""
	match = re.search(r"^\|\s*location\d?\s*=(.*)$", text, re.M)
	location = plain(match.group(1)) if match else ""
	if not location:
		return []
	return [{"location": location, "name": "", "page": "", "levels": set(), "coords": map_points(text), "wilderness": False}]


def on_surface(coord):
	return SURFACE_Y[0] <= coord[1] < SURFACE_Y[1]


def off_surface(coords):
	return bool(coords) and not any(on_surface(coord) for coord in coords)


def surface_entrances(page_text):
	"""@return the surface points a place's wiki page marks, which for a dungeon are its entrances"""
	found = []
	for coord in map_points(page_text):
		if on_surface(coord) and coord not in found:
			found.append(coord)
	return found[:MAX_ENTRANCES]


def load_transports():
	"""@return (surface origin, off-surface destination) for every one-step way down from the surface"""
	links = []
	with fetch(TRANSPORTS) as resp:
		for raw in resp.read().decode("utf-8").splitlines():
			fields = raw.split("\t")
			if raw.startswith("#") or len(fields) < 2:
				continue
			try:
				origin = tuple(int(v) for v in fields[0].split())
				destination = tuple(int(v) for v in fields[1].split())
			except ValueError:
				continue
			if len(origin) == 3 and len(destination) == 3 and on_surface(origin) and not on_surface(destination):
				links.append((origin, destination))
	return links


def transport_entrances(coords, links):
	"""@return the origins of the transports landing within LANDING_RANGE of the monsters, nearest landing first"""
	found = []
	for origin, destination in links:
		distances = [max(abs(destination[0] - x), abs(destination[1] - y)) for x, y, plane in coords if plane == destination[2]]
		if distances and min(distances) <= LANDING_RANGE:
			found.append((min(distances), origin))
	return [origin for _, origin in sorted(found)]


def add_entrance(entrances, coord):
	if len(entrances) < MAX_ENTRANCES and all(
		max(abs(coord[0] - e[0]), abs(coord[1] - e[1])) >= SAME_ENTRANCE or coord[2] != e[2] for e in entrances):
		entrances.append(coord)


def version_words(version):
	"""'Level 104 (Wilderness Slayer Cave)' -> 'wilderness slayer cave'; '' for versions naming no place."""
	words = re.sub(r"\blevel\s*\d+\b|[()]", " ", version.lower())
	words = re.sub(r"\s+", " ", words).strip()
	return "" if not words or words.isdigit() or words in ("standard", "normal", "default") else words


def belongs_to(line, version):
	words = version_words(version)
	if not words:
		return False
	place = (line["location"] + " " + line["name"]).lower()
	return words in place or line["location"].lower() in words


def pick(lines, monster, versions):
	"""@return the location lines for one version of a monster page"""
	version = monster.get("version") or ""
	if not version or len(versions) < 2:
		return lines
	own = [line for line in lines if belongs_to(line, version)]
	if own:
		return own
	others = [v for v in versions if v != version]
	rest = [line for line in lines if not any(belongs_to(line, v) for v in others)] or lines
	level = str(monster.get("level"))
	same_level = [line for line in rest if level in line["levels"]]
	return same_level or rest


def main():
	monsters = json.loads((DATA / "monsters.json").read_text())
	names = sorted({m["name"] for m in monsters})
	versions = {}
	for m in monsters:
		versions.setdefault(m["name"], []).append(m.get("version") or "")

	pages = {}
	for i in range(0, len(names), BATCH):
		pages.update(fetch_pages(names[i:i + BATCH]))
		print(f"fetched {min(i + BATCH, len(names))}/{len(names)} pages", flush=True)
		time.sleep(0.5)

	chosen = {}
	for m in monsters:
		text = pages.get(m["name"])
		if not text:
			continue
		places = {}
		for line in pick(parse_lines(text), m, versions[m["name"]]):
			place = places.setdefault(line["location"], {"coords": [], "page": line["page"], "wilderness": False})
			place["wilderness"] = place["wilderness"] or line["wilderness"]
			for coord in line["coords"]:
				if coord not in place["coords"]:
					place["coords"].append(coord)
		if places:
			chosen[m["id"]] = places

	# Entrances for the places off the surface map, from each place's own wiki page
	place_pages = sorted({place["page"] for places in chosen.values() for place in places.values()
		if place["page"] and off_surface(place["coords"])})
	location_pages = {}
	for i in range(0, len(place_pages), BATCH):
		location_pages.update(fetch_pages(place_pages[i:i + BATCH]))
		time.sleep(0.5)
	links = load_transports()
	print(f"fetched {len(location_pages)} location pages, {len(links)} ways down", flush=True)
	# Every spawn in each dungeon, so a way down near any of its monsters counts for all of them
	dungeon_coords = {}
	for places in chosen.values():
		for place in places.values():
			if place["page"] and off_surface(place["coords"]):
				dungeon_coords.setdefault(place["page"], set()).update(place["coords"])

	out = {}
	entered = underground = 0
	for npc_id, places in chosen.items():
		rows = []
		for name, place in places.items():
			row = {"name": name, "points": [c for coord in place["coords"] for c in coord]}
			if place["wilderness"]:
				row["wilderness"] = True
			if off_surface(place["coords"]):
				underground += 1
				row["underground"] = True
				entrances = []
				for coord in transport_entrances(dungeon_coords.get(place["page"], place["coords"]), links):
					add_entrance(entrances, coord)
				for coord in surface_entrances(location_pages.get(place["page"], "")):
					add_entrance(entrances, coord)
				if entrances:
					entered += 1
					row["entrances"] = [c for coord in entrances for c in coord]
			rows.append(row)
		out[str(npc_id)] = rows

	(DATA / "locations.json").write_text(json.dumps(out, separators=(",", ":")))
	print(f"locations.json: {len(out)} of {len(monsters)} monsters; {entered} of {underground} underground places have entrances")


if __name__ == "__main__":
	main()
