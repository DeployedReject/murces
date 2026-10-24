"""Only a standalone 'release' in a commit from this push enables a stable release."""
import json
import os
import re
import subprocess
from pathlib import Path


def is_prerelease(event_name, event):
    if event_name != "push":
        return True
    before, after = event["before"], event["after"]
    revision = after if set(before) == {"0"} else f"{before}..{after}"
    # Use the full git range: push payloads can truncate the commits array.
    messages = subprocess.check_output(
        ["git", "log", "--format=%B%x00", revision], text=True
    )
    return re.search(r"\brelease\b", messages, re.IGNORECASE) is None


if __name__ == "__main__":
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
    result = is_prerelease(os.environ["GITHUB_EVENT_NAME"], event)
    with open(os.environ["GITHUB_OUTPUT"], "a") as output:
        output.write(f"prerelease={str(result).lower()}\n")
