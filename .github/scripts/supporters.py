#!/usr/bin/env python3
"""Writes the Supporters list in Dejavu's README from the active Buy Me a Coffee memberships.

Usage: supporters.py < README.md > README.new.md
Reads the members from the Buy Me a Coffee API with the token in the BMC_TOKEN environment variable, and replaces what
is between the supporters:start and supporters:end markers. Members are grouped by level (Neo first), by how much they
pay a month: a yearly membership counts as a tenth of its price, since a year costs ten months. Members who keep their
support private are left out, and only the name they chose on Buy Me a Coffee is shown, never their email.
"""
import json
import os
import re
import sys
import unicodedata
import urllib.request

START, END = "<!-- supporters:start -->", "<!-- supporters:end -->"
JOIN = "https://buymeacoffee.com/ByArchitect/membership"
# Monthly price each level starts at, highest first. Keep in step with the levels on Buy Me a Coffee.
LEVELS = [("Neo", 15), ("Trinity", 7), ("Morpheus", 3)]
MAX_NAME = 40


def members():
    url = "https://developers.buymeacoffee.com/api/v1/subscriptions?status=active"
    while url:
        request = urllib.request.Request(
            url, headers={"Authorization": f"Bearer {os.environ['BMC_TOKEN']}", "User-Agent": "dejavu-supporters"}
        )
        with urllib.request.urlopen(request) as response:
            page = json.load(response)
        yield from page.get("data") or []
        url = page.get("next_page_url")


def level(member):
    monthly = float(member.get("subscription_coffee_price") or 0) * int(member.get("subscription_coffee_num") or 1)
    if str(member.get("subscription_duration_type", "")).lower().startswith("year"):
        monthly /= 10
    # Prices are whole dollars; the small margin keeps a level when a currency conversion rounds it down by a cent.
    return next((name for name, price in LEVELS if monthly >= price - 0.05), None)


def clean(name):
    # Only letters, numbers, spaces and simple punctuation, so a name cannot add links, pictures or markup.
    name = "".join(c for c in unicodedata.normalize("NFC", name or "") if unicodedata.category(c)[0] in "LNZ" or c in ".-'")
    name = re.sub(r"\s+", " ", name).strip()
    return name[: MAX_NAME - 1].rstrip() + "…" if len(name) > MAX_NAME else name


def section(groups):
    if not any(groups.values()):
        return f"No members yet. Be the first: [join the crew]({JOIN})."
    lines = [f"**{name}** · " + " · ".join(groups[name]) for name, _ in LEVELS if groups[name]]
    return "  \n".join(lines) + f"\n\nThank you all. [Join the crew]({JOIN})."


def main():
    readme = sys.stdin.read()
    if START not in readme or END not in readme:
        sys.exit("The README has no supporters markers.")
    groups = {name: [] for name, _ in LEVELS}
    for member in sorted(members(), key=lambda m: m.get("subscription_created_on") or ""):
        # message_visibility 1 means the member made their support public.
        if member.get("message_visibility") != 1:
            continue
        name, where = clean(member.get("payer_name")), level(member)
        if name and where:
            groups[where].append(name)
    before, rest = readme.split(START, 1)
    after = rest.split(END, 1)[1]
    sys.stdout.write(f"{before}{START}\n{section(groups)}\n{END}{after}")


if __name__ == "__main__":
    main()
