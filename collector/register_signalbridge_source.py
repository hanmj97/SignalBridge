"""Safely register this project's GitHub Releases feed as an existing collector source.

Preview (no DB/API access): python -m collector.register_signalbridge_source
Verify and register:       python -m collector.register_signalbridge_source --apply
Explicit repo override:   python -m collector.register_signalbridge_source --repo owner/repo --apply

The project must have a GitHub remote and GitHub Releases, not merely commits/tags.
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
from pathlib import Path
from urllib.parse import urlsplit

import requests
from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parents[1]
SOURCE_KEY = "github_signalbridge_self"
SOURCE_LABEL = "SignalBridge (프로젝트)"
REPO_PATTERN = re.compile(r"^[A-Za-z0-9-]{1,39}/[A-Za-z0-9_.-]{1,100}$")


def validate_repo(repo: str) -> str:
    repo = repo.strip()
    if not REPO_PATTERN.fullmatch(repo):
        raise ValueError("GitHub repository must be owner/repo (e.g. my-account/SignalBridge)")
    owner, name = repo.split("/", 1)
    if owner.startswith("-") or owner.endswith("-") or name in (".", "..") or name.endswith(".git"):
        raise ValueError("Invalid GitHub owner/repository identifier")
    return repo


def repo_from_remote(remote: str) -> str:
    """Only accept GitHub remotes; never read user credentials from URLs."""
    remote = remote.strip()
    if remote.startswith("git@github.com:"):
        slug = remote[len("git@github.com:"):]
    else:
        parsed = urlsplit(remote)
        if parsed.scheme not in ("https", "ssh") or parsed.hostname != "github.com":
            raise ValueError("origin must point to github.com; use --repo owner/repo")
        if parsed.username not in (None, "git") or parsed.password or parsed.port or parsed.query or parsed.fragment:
            raise ValueError("origin URL contains unexpected credentials or options; use --repo owner/repo")
        slug = parsed.path.lstrip("/")
    if slug.endswith(".git"):
        slug = slug[:-4]
    if slug.endswith("/"):
        slug = slug[:-1]
    return validate_repo(slug)


def discover_origin() -> str:
    try:
        p = subprocess.run(
            ["git", "-C", str(ROOT), "remote", "get-url", "origin"],
            capture_output=True, text=True, check=False, timeout=8,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise ValueError("Could not read git origin; pass --repo owner/repo") from exc
    if p.returncode != 0:
        raise ValueError("No git origin set; pass --repo owner/repo or configure origin")
    return repo_from_remote(p.stdout)


def verify_api_access(repo: str) -> int:
    """Fail closed: do not register inaccessible repositories and break existing Airflow DAGs."""
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "SignalBridge-Release-Monitor"}
    token = os.getenv("GITHUB_TOKEN", "").strip()
    if token:
        headers["Authorization"] = f"Bearer {token}"
    try:
        result = requests.get(
            f"https://api.github.com/repos/{repo}/releases",
            headers=headers, params={"per_page": 1}, timeout=15,
        )
    except requests.RequestException as exc:
        raise RuntimeError("GitHub API request failed. Check network and retry") from exc
    if result.status_code != 200:
        raise RuntimeError(
            f"GitHub API HTTP {result.status_code}. Check repository visibility, GITHUB_TOKEN and rate limits"
        )
    try:
        body = result.json()
    except ValueError as exc:
        raise RuntimeError("GitHub API returned invalid JSON") from exc
    if not isinstance(body, list):
        raise RuntimeError("GitHub API response is not a release list")
    return len(body)


def register(repo: str) -> str:
    # Reuses the operational SignalBridge database, not signalbridge_v2_lab.
    from collector.main import connect

    if os.getenv("POSTGRES_DB", "signalbridge") != "signalbridge":
        raise RuntimeError("POSTGRES_DB must be signalbridge (operational GitHub database)")
    with connect() as conn:
        with conn.transaction():
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT provider, resource, enabled FROM sources WHERE source_key=%s FOR UPDATE",
                    (SOURCE_KEY,),
                )
                old = cur.fetchone()
                if old is None:
                    cur.execute(
                        """INSERT INTO sources (source_key, label, provider, resource, enabled)
                           VALUES (%s, %s, 'github_releases', %s, TRUE)""",
                        (SOURCE_KEY, SOURCE_LABEL, repo),
                    )
                    return "ADDED"
                provider, resource, enabled = old
                if provider != "github_releases" or resource != repo:
                    raise RuntimeError(
                        "This source_key already tracks another resource. No changes made; inspect sources manually"
                    )
                if not enabled:
                    cur.execute("UPDATE sources SET enabled=TRUE WHERE source_key=%s", (SOURCE_KEY,))
                    return "RE-ENABLED"
                return "ALREADY EXISTS"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Register SignalBridge project's GitHub Releases")
    parser.add_argument("--repo", help="Explicit owner/repo if git origin is not configured")
    parser.add_argument("--apply", action="store_true", help="Verify GitHub API, then write the source row")
    args = parser.parse_args(argv)
    try:
        repo = validate_repo(args.repo) if args.repo else discover_origin()
        print(f"GitHub Releases source: {repo}")
        if not args.apply:
            print("PREVIEW ONLY: no API call or database write. Use --apply to register")
            return 0
        load_dotenv(ROOT / ".env")
        count = verify_api_access(repo)
        result = register(repo)
        print(f"Source registration: {result} ({SOURCE_KEY})")
        if count == 0:
            print("No GitHub Release found yet. Publish a GitHub Release to create tracked entries")
        return 0
    except (ValueError, RuntimeError) as exc:
        print(f"Registration aborted: {exc}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
