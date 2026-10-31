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
    assert not re.search(r"0x[0-9a-fA-F]{6,8}\b", text), (
        f"{path.name}: extract colors into theme resources"
    )
    assert not re.search(r"\b\d+(?:\.\d+)?\.(?:dp|sp)\b", text), (
        f"{path.name}: use the shared dimension or typography scale"
    )
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

values = project / "app/src/main/res/values"
for kind, filename in [("dimen", "dimens.xml"), ("attr", "attrs.xml")]:
    definitions = ET.parse(values / filename).getroot().findall(kind)
    names = {entry.attrib["name"] for entry in definitions}
    assert len(names) == len(definitions), f"Duplicate {kind} resource names"
    for path in source_files:
        for name in re.findall(rf"(?<!android\.)R\s*\.\s*{kind}\s*\.\s*(\w+)", path.read_text()):
            assert name in names, f"{path.name}: missing {kind} resource {name}"

roles = {item.attrib["name"] for item in ET.parse(values / "attrs.xml").getroot()}
for qualifier in ["values", "values-night"]:
    folder = values.parent / qualifier
    colors = {item.attrib["name"] for item in ET.parse(folder / "colors.xml").getroot()}
    palettes = {item.attrib["name"]: item for item in ET.parse(folder / "palettes.xml").getroot()}
    for name in ["Palette.X", "Palette.Play"]:
        items = palettes[name].findall("item")
        assert {item.attrib["name"] for item in items} == roles, f"{qualifier}/{name}: incomplete palette"
        for item in items:
            assert item.text.removeprefix("@color/") in colors, f"{qualifier}/{name}: missing color {item.text}"
print("PASS: shared dimensions, theme roles and light/dark palettes checked")
