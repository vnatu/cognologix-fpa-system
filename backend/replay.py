#!/usr/bin/env python3
"""Replay one logged bank-reconciliation LLM call against oMLX.
Read-only: no database, no app code. Run it from backend/.

usage:  OMLX_KEY=... python3 replay.py CALL_ID MAX_TOKENS [MODE] [TEMP]
  CALL_ID     e.g. 'R-004#41'   (ids repeat across runs; the last match in the trace is used)
  MAX_TOKENS  output ceiling, e.g. 900 or 12000
  MODE        default | off | suffix
                off    -> chat_template_kwargs {"enable_thinking": false}
                suffix -> appends " /no_think" to the user message
  TEMP        sampling temperature (default 0)
env:
  OMLX_KEY (required unless NO_CALL=1), OMLX_URL, OMLX_MODEL, TRACE (default logs/llm-trace.log)
  SHOW_NARRATION=0   hide narrations in the printed rows
  GUIDE_FILE=path    text injected under TRANSACTION HINTS (or appended to the system message)
  CHECK="a,b"        show which catalog lines contain each text, and under which section
  NO_CALL=1          do the checks only; make no model call
"""
import json, os, re, sys, time, urllib.error, urllib.request

TRACE = os.environ.get("TRACE", "logs/llm-trace.log")
BASE = os.environ.get("OMLX_URL", "http://localhost:9000").rstrip("/")
MODEL = os.environ.get("OMLX_MODEL", "mlx-community/Qwen3.6-35B-A3B-4bit")
SHOW_NARRATION = os.environ.get("SHOW_NARRATION", "1") != "0"


def read_lines():
    with open(TRACE, encoding="utf-8", errors="replace") as f:
        return f.read().splitlines()


def after_prefix(line, prefix):
    """Text after 'callId=X ', or None if the line does not belong to this call."""
    if line == prefix:
        return ""
    if line.startswith(prefix + " "):
        return line[len(prefix) + 1:]
    return None


def load(call_id):
    lines = read_lines()
    p = f"callId={call_id}"
    starts = [i for i, l in enumerate(lines) if l.endswith(p + " system:")]
    if not starts:
        sys.exit(f"no 'system:' block for {call_id} in {TRACE}")
    parts, cur = {"system": [], "user": []}, "system"
    for l in lines[starts[-1] + 1:]:          # last match, because ids repeat across runs
        text = after_prefix(l, p)
        if text is None or text == "options:":
            break
        if text == "user:":
            cur = "user"
        else:
            parts[cur].append(text)
    if parts["system"] and parts["system"][0] == "SYSTEM":
        parts["system"] = parts["system"][1:]
    return "\n".join(parts["system"]).strip(), "\n".join(parts["user"]).strip()


def find_catalog():
    """The longest full LEDGER CATALOG block in the trace (later calls only log a hash line)."""
    best, cur = [], None
    for l in read_lines():
        m = re.search(r"callId=\S+(?: (.*))?$", l)
        if not m:
            continue
        text = m.group(1) or ""
        if text == "LEDGER CATALOG":
            cur = []
        elif text == "TRANSACTIONS":
            if cur is not None and len(cur) > len(best):
                best = cur
            cur = None
        elif cur is not None:
            cur.append(text)
    return best


def catalog_names(cat):
    return {l.strip().split(" | ")[0].strip() for l in cat if l.strip()}


def parse_rows(text):
    """Last JSON array of objects with an id and a ledger field."""
    text = text.split("</think>")[-1]
    dec, found, i = json.JSONDecoder(), [], 0
    while True:
        i = text.find("[", i)
        if i < 0:
            break
        try:
            obj, end = dec.raw_decode(text[i:])
            if isinstance(obj, list) and obj and all(isinstance(x, dict) for x in obj) \
                    and any(("transactionId" in x or "id" in x) for x in obj):
                found.append(obj)
                i += end
                continue
        except ValueError:
            pass
        i += 1
    return found[-1] if found else None


def show_catalog_checks(cat, wanted):
    """Which catalog lines contain each text, with the last PAYMENT/RECEIPT heading seen above it."""
    section, found = "?", {w: [] for w in wanted}
    for line in cat:
        s = line.strip()
        h = re.match(r"^[#=*\- ]*(PAYMENT|RECEIPT)\b", s)
        if h and " | " not in s:
            section = h.group(1)
        for w in wanted:
            if w.lower() in s.lower():
                found[w].append(f"[{section}] {s[:90]}")
    print("catalog visibility ('?' means no PAYMENT/RECEIPT heading was recognised above the line):")
    for w, hits in found.items():
        print(f"  '{w}': {len(hits)} hit(s)")
        for h in hits[:4]:
            print("     ", h)


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)
    call_id, max_tokens = sys.argv[1], int(sys.argv[2])
    mode = sys.argv[3] if len(sys.argv) > 3 else "default"
    temp = float(sys.argv[4]) if len(sys.argv) > 4 else 0.0
    if mode not in ("default", "off", "suffix"):
        sys.exit("MODE must be default, off or suffix")
    no_call = os.environ.get("NO_CALL") == "1"
    key = os.environ.get("OMLX_KEY")
    if not key and not no_call:
        sys.exit("set OMLX_KEY first (read -rs OMLX_KEY && export OMLX_KEY)")

    system, user = load(call_id)
    cat = find_catalog()
    names = catalog_names(cat)
    restored = False
    hash_line = re.compile(r"catalog: \S+ \(\d+ ledgers, unchanged\)")
    if hash_line.search(user):
        if len(cat) < 50:
            sys.exit("the trace has no full catalog block to restore; send me lines around a LEDGER CATALOG block")
        user = hash_line.sub(lambda m: "\n".join(cat), user, count=1)
        restored = True
    print(f"system {len(system)} chars | user {len(user)} chars | catalog lines {len(cat)} | restored {restored}")

    guide = os.environ.get("GUIDE_FILE")
    if guide:
        with open(guide, encoding="utf-8") as f:
            text = f.read().strip()
        marker = "TRANSACTION HINTS\n(none)"
        if marker in user:
            user = user.replace(marker, "TRANSACTION HINTS\n" + text, 1)
            where = "under TRANSACTION HINTS"
        else:
            system = system + "\n\n" + text
            where = "appended to the system message (hints were not empty)"
        print(f"guidelines injected from {guide} ({len(text)} chars), {where}")

    wanted = [w.strip() for w in os.environ.get("CHECK", "").split(",") if w.strip()]
    if wanted:
        show_catalog_checks(cat, wanted)
    if no_call:
        return
    print("(a real prompt is about 9,670 tokens, so check prompt_tokens below)")

    body = {"model": MODEL, "max_tokens": max_tokens, "temperature": temp,
            "messages": [{"role": "system", "content": system},
                         {"role": "user", "content": user + (" /no_think" if mode == "suffix" else "")}]}
    if mode == "off":
        body["chat_template_kwargs"] = {"enable_thinking": False}

    req = urllib.request.Request(BASE + "/v1/chat/completions", json.dumps(body).encode(),
                                 {"Authorization": "Bearer " + key, "Content-Type": "application/json"})
    t0 = time.time()
    try:
        d = json.load(urllib.request.urlopen(req, timeout=1800))
    except urllib.error.HTTPError as e:
        sys.exit(f"HTTP {e.code}: {e.read().decode('utf-8', 'replace')[:500]}")
    secs = time.time() - t0

    choice = d["choices"][0]
    msg = choice["message"]
    content = msg.get("content") or ""
    reasoning = msg.get("reasoning_content") or msg.get("reasoning") or ""
    usage = d.get("usage", {})
    cached = (usage.get("prompt_tokens_details") or {}).get("cached_tokens")
    ctoks = usage.get("completion_tokens")

    print(f"\nmode={mode} temp={temp} max_tokens={max_tokens} | wall time {secs:.1f}s")
    print(f"finish_reason: {choice.get('finish_reason')}")
    print(f"prompt_tokens={usage.get('prompt_tokens')} cached_tokens={cached} "
          f"completion_tokens={ctoks} server_total_time={usage.get('total_time')}")
    print(f"content: {len(content)} chars | reasoning field: {len(reasoning)} chars | "
          f"has </think> in content: {'</think>' in content}")
    print("content starts:", content[:150].replace("\n", " "))
    print("content ends:  ", content[-150:].replace("\n", " "))

    rows = parse_rows(content)
    if rows is None:
        print("\nno JSON array parsed from content")
        return
    narr = {i: (n, a, t) for i, n, a, t in
            re.findall(r'- id=(\d+) narration="(.*?)" amount=([\d.]+) type=(\w+)', user)}
    print(f"\nparsed {len(rows)} rows")
    ok = bad = unm = 0
    for r in rows:
        i = str(r.get("transactionId", r.get("id")))
        name = str(r.get("ledgerName", r.get("ledger")))
        if name == "UNMAPPED":
            flag, unm = "UNMAPPED", unm + 1
        elif name in names:
            flag, ok = "in catalog", ok + 1
        else:
            flag, bad = "NOT IN CATALOG", bad + 1
        n, a, t = narr.get(i, ("", "", ""))
        shown = f"  <-  {n[:60]} | {a} | {t}" if SHOW_NARRATION else ""
        print(f"  {i}: {name}  [{flag}]{shown}")
    print(f"\nin catalog {ok} | not in catalog {bad} | UNMAPPED {unm}")


main()
