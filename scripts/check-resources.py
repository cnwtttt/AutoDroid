#!/usr/bin/env python3
"""Check presentation-resource coverage without an Android runtime."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

project = Path(__file__).resolve().parent.parent
resources = project / "app/src/main/res/values/strings.xml"
entries = ET.parse(resources).getroot().findall("string")
known = {entry.attrib["name"] for entry in entries}
assert len(known) == len(entries), "Duplicate string resource names"
source_files = list((project / "app/src/main/java").rglob("*.java"))
source_files += list((project / "app/src/main/java").rglob("*.kt"))
source_files += list((project / "native-app/src/main/java").rglob("*.java"))
for path in source_files:
    text = path.read_text()
    for key in re.findall(r"(?<!android\.)R\s*\.\s*string\s*\.\s*(\w+)", text):
        assert key in known, f"{path.name}: missing string resource {key}"
    for literal in re.findall(r'"(?:[^"\\]|\\.)*"', text):
        assert not re.search(r"[\u4e00-\u9fff]", literal), (
            f"{path.name}: extract presentation text into resources: {literal}"
        )
for entry in entries:
    if entry.attrib.get("formatted") != "false":
        value = entry.text or ""
        for token in re.findall(r"%(?:\d+\$)?[a-zA-Z%]", value):
            assert token == "%%" or re.fullmatch(r"%\d+\$[sd]", token), (
                f"{entry.attrib['name']}: use positional format arguments: {token}"
            )
print(f"PASS: {len(entries)} strings; references, literals and format placeholders checked")
