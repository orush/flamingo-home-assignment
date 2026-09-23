#!/usr/bin/env python3
"""Fail if a credential appears in any file about to be uploaded as a CI artifact.

This repository is public, so its CI artifacts can be downloaded by anyone. The
framework masks credentials before they reach a report; this check enforces it
on every run instead of trusting it.

Values come from the environment and are never printed: a hit reports only the
file and which rule matched. Scanning nothing is treated as an error, because a
scan that silently inspected no files looks exactly like a clean one.

Usage: check_no_secrets.py DIR [DIR ...]
"""
import os
import pathlib
import re
import sys


def rules():
    password = os.environ.get("BOOKER_PASSWORD", "").encode()
    username = os.environ.get("BOOKER_USERNAME", "").encode()
    if not password or not username:
        sys.exit("::error::BOOKER_USERNAME and BOOKER_PASSWORD must be set for the secret check")
    quote = rb'(?:"|\'|&quot;|&#34;)'
    return [
        ("password", re.compile(re.escape(password))),
        # A username may be an ordinary word, so only a quoted value counts.
        ("username", re.compile(quote + re.escape(username) + quote)),
        # Restful Booker session tokens are 15 hex characters.
        ("session token", re.compile(rb"(?i)token[^0-9a-f]{1,20}[0-9a-f]{15}(?![0-9a-f])")),
    ]


def main(roots):
    checks = rules()
    scanned, hits = 0, []
    for root in roots:
        for path in pathlib.Path(root).rglob("*"):
            if not path.is_file():
                continue
            scanned += 1
            data = path.read_bytes()
            hits += [(name, path) for name, pattern in checks if pattern.search(data)]

    if scanned == 0:
        print(f"::error::No files found under {roots}; refusing to report a clean scan")
        return 3
    for name, path in hits:
        print(f"::error file={path}::A {name} was found in an artifact")
    print(f"Scanned {scanned} files: {len(hits)} credential leak(s)")
    return 1 if hits else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
