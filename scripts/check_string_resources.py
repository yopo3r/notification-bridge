#!/usr/bin/env python3
"""Fails with a clear message if any string resource has an unescaped apostrophe.

AAPT2 rejects an unescaped apostrophe in a <string> value, but its error for this is a
near-useless "Can not extract resource from com.android.aaptcompiler.ParsedResource@...".
Run this before a real build to get a message that actually names the file and the key.

Usage: python3 scripts/check_string_resources.py
"""
import glob
import re
import sys

ROOT = "app/src/main/res"


def find_unescaped_apostrophes() -> list[tuple[str, str, str]]:
    problems = []
    for path in sorted(glob.glob(f"{ROOT}/values*/strings.xml")):
        content = open(path, encoding="utf-8").read()
        for m in re.finditer(r'<string name="([^"]+)">(.*?)</string>', content, re.S):
            name, value = m.group(1), m.group(2)
            # A correctly escaped apostrophe is "\'"; remove those first, then anything
            # left is a raw apostrophe AAPT2 will refuse to compile.
            if "'" in value.replace(r"\'", ""):
                problems.append((path, name, value))
    return problems


def main() -> int:
    problems = find_unescaped_apostrophes()
    if not problems:
        print("OK: no unescaped apostrophes found.")
        return 0

    for path, name, value in problems:
        snippet = value if len(value) <= 60 else value[:57] + "..."
        print(f"{path}: unescaped apostrophe in '{name}': {snippet}")
        print(f"  fix: replace ' with \\' in that string")
    print(f"\n{len(problems)} string(s) need fixing before this will compile.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
