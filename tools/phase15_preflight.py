#!/usr/bin/env python3
"""Read-only preflight for SignalBridge Git tracking, secrets, and local .env.

Never prints secret contents. Stdlib only. Does not modify any project files.
"""
from __future__ import annotations

import argparse
import base64
import binascii
import os
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import urlsplit

SECRET_NAME = "SIGNALBRIDGE_DISCORD_ENCRYPTION_KEY"
ENV_CONFIGS = ("POSTGRES_PASSWORD", "RESEND_API_KEY", SECRET_NAME, "SIGNALBRIDGE_PUBLIC_URL")
TOKEN_RULES = (
    ("Resend secret", re.compile(r"\bre_[A-Za-z0-9_]{20,}\b")),
    ("GitHub token", re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,})\b")),
    ("Discord webhook", re.compile(r"https?://(?:discord(?:app)?\.com)/api/webhooks/[0-9]{10,}/[A-Za-z0-9_-]{15,}")),
    ("Discord AES key literal", re.compile(r"^\s*SIGNALBRIDGE_DISCORD_ENCRYPTION_KEY\s*=\s*[A-Za-z0-9+/]{40,}={0,2}\s*$", re.M)),
)
SENSITIVE_FILE_NAMES = {".env", "id_rsa", "id_ed25519"}
SENSITIVE_SUFFIXES = (".dump", ".bak", ".backup", ".p12", ".pfx", ".pem", ".sqlite", ".sqlite3")
SCANNABLE_SUFFIXES = {".py", ".java", ".ts", ".tsx", ".js", ".yml", ".yaml", ".md", ".txt", ".sql", ".json", ".properties", ".sh", ".ps1", ".xml", ".env"}
MAX_FILE_SIZE = 2_000_000


def tracked_sensitive_path(relative: str) -> bool:
    rel = relative.replace("\\", "/")
    parts = rel.split("/")
    name = parts[-1].lower()
    if name in SENSITIVE_FILE_NAMES or name.startswith(".env.") and name != ".env.example":
        return True
    if any(s in parts for s in ("backups", "node_modules", "__pycache__", ".venv", "_phase9_staging", "_phase10_email_staging")):
        return True
    if name.endswith(SENSITIVE_SUFFIXES):
        return True
    if name.endswith(".key") and not name.endswith(".pub.key"):
        return True
    return False


def parse_env(source: str):
    """Parse key/value lines without expansion. Returns mapping and duplicate names."""
    values = {}
    duplicates = set()
    for line in source.splitlines():
        value = line.strip()
        if not value or value.startswith("#") or "=" not in value:
            continue
        key, data = value.split("=", 1)
        key = key.strip()
        if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", key):
            continue
        if key in values:
            duplicates.add(key)
        values[key] = data.strip().strip('"').strip("'")
    return values, duplicates


def inspect_env(root: Path):
    errors, warnings = [], []
    path = root / ".env"
    if not path.exists():
        warnings.append("Local .env not present; development-only config checks skipped")
        return errors, warnings
    try:
        raw = path.read_bytes()
        source = raw.decode("utf-8-sig")
    except UnicodeDecodeError:
        errors.append(".env is not UTF-8; inspect with your editor, do not overwrite with a lossy encoding")
        return errors, warnings
    values, duplicates = parse_env(source)
    for key in sorted(duplicates):
        errors.append(f"Duplicate .env setting: {key}")
    if "\ufffd" in source:
        warnings.append(".env contains a Unicode replacement character; check previous encoding damage")
    if any("\uac00" <= c <= "\ud7a3" for c in source) and "?" in source:
        # Do not overclaim detected corruption; this is a soft reminder only.
        pass
    url = values.get("SIGNALBRIDGE_PUBLIC_URL")
    if url:
        try:
            p = urlsplit(url)
            if p.scheme not in ("http", "https") or not p.hostname or "SIGNALBRIDGE_" in url or "POSTGRES_" in url:
                errors.append("SIGNALBRIDGE_PUBLIC_URL is malformed or has appended variable text")
        except ValueError:
            errors.append("SIGNALBRIDGE_PUBLIC_URL is malformed")
    else:
        warnings.append("SIGNALBRIDGE_PUBLIC_URL not set in .env")
    for name in ("RESEND_API_KEY", SECRET_NAME):
        if not values.get(name):
            warnings.append(f"{name} not set (required only for its associated feature)")
    k = values.get(SECRET_NAME)
    if k:
        try:
            data = base64.b64decode(k, validate=True)
            if len(data) != 32:
                errors.append(f"{SECRET_NAME} must decode to exactly 32 bytes")
        except (ValueError, binascii.Error):
            errors.append(f"{SECRET_NAME} is not valid Base64")
    resend = values.get("RESEND_API_KEY", "")
    if resend and not resend.startswith("re_"):
        errors.append("RESEND_API_KEY does not have the expected prefix")
    if values.get("POSTGRES_PASSWORD", "") in {"local_dev_change_me", "postgres", "password", "123456"}:
        warnings.append("PostgreSQL password looks like a development default; do not deploy with it")
    if values.get("SIGNALBRIDGE_PUBLIC_URL", "").startswith("http://localhost"):
        warnings.append("PUBLIC_URL uses localhost; this is fine for development but not deployment")
    return errors, warnings


def git_tracked(root: Path):
    try:
        result = subprocess.run(["git", "-C", str(root), "ls-files", "-z"], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=True)
        return [os.fsdecode(name) for name in result.stdout.split(b"\0") if name], None
    except (FileNotFoundError, subprocess.CalledProcessError):
        return [], "Git not initialized/available; tracked-file secret scan skipped"


def inspect_tracked(root: Path, paths: list[str]):
    errors, warnings = [], []
    for rel in paths:
        if tracked_sensitive_path(rel):
            errors.append(f"Tracked sensitive/generated file: {rel}")
        path = root / rel
        if not path.is_file() or path.suffix.lower() not in SCANNABLE_SUFFIXES or path.stat().st_size > MAX_FILE_SIZE:
            continue
        try:
            contents = path.read_text(encoding="utf-8")
        except (UnicodeError, OSError):
            continue
        for label, rule in TOKEN_RULES:
            for match in rule.finditer(contents):
                line_no = contents.count("\n", 0, match.start()) + 1
                # Never print matching text.
                errors.append(f"Possible {label} in tracked file: {rel}:{line_no} (value hidden)")
    return errors, warnings


def run(root: Path, ci: bool):
    print("SignalBridge Phase 15 / read-only preflight")
    print("Secrets will never be printed")
    errors, warnings = inspect_env(root) if not ci else ([], [])
    paths, git_warning = git_tracked(root)
    if git_warning:
        (errors if ci else warnings).append(git_warning)
    else:
        e, w = inspect_tracked(root, paths)
        errors.extend(e)
        warnings.extend(w)
        print(f"Git tracked files inspected: {len(paths)}")
    for entry in warnings:
        print(f"[WARN] {entry}")
    for entry in errors:
        print(f"[FAIL] {entry}")
    print(f"Result: {len(errors)} errors, {len(warnings)} warnings")
    return 1 if errors else 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--ci", action="store_true", help="only scan tracked files; do not require .env in CI")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    sys.exit(run(args.root.resolve(), args.ci))
