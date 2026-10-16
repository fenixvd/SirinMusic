#!/usr/bin/env python3
"""Проверить постоянную версию Gradle перед публикацией тега."""
import argparse
import json
import re
from pathlib import Path


def read_version(path):
    source = path.read_text(encoding="utf-8")
    names = re.findall(r'^\s*versionName = "([^"\n]+)"\s*$', source, re.MULTILINE)
    codes = re.findall(r'^\s*versionCode = ([0-9]+)\s*$', source, re.MULTILINE)
    if len(names) != 1 or len(codes) != 1 or int(codes[0]) <= 0:
        raise ValueError("Нужны единственные явные versionName и положительный versionCode в app/build.gradle.kts")
    return {"versionName": names[0], "versionCode": int(codes[0])}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    args = parser.parse_args()
    version = read_version(Path(__file__).resolve().parents[1] / "app/build.gradle.kts")
    if args.tag != "v" + version["versionName"]:
        parser.error(f"Тег {args.tag} не соответствует versionName={version['versionName']}")
    print(json.dumps(version, ensure_ascii=False))
