#!/usr/bin/env python3
"""OCR probe for a scanned/image PDF statement. Prints the SHAPE of each page, not the data.

Usage:  python3 ocr_probe.py statement.pdf [--dpi 300] [--show]
Needs:  brew install poppler tesseract   (pdftoppm + tesseract on PATH)
Output: per page, the text lines as masked patterns (digits -> 9, letters -> a/A),
        x-position clusters (column guesses) and OCR confidence.
        With --show the real text is printed too (do NOT paste that if it has card/PII data).
"""
import csv, io, os, re, subprocess, sys, tempfile, glob, statistics

def run(cmd):
    return subprocess.run(cmd, capture_output=True, text=True, check=True).stdout

def mask(s):
    s = re.sub(r"[0-9]", "9", s)
    s = re.sub(r"[A-Z]", "A", s)
    return re.sub(r"[a-z]", "a", s)

def main():
    args = sys.argv[1:]
    if not args or args[0].startswith("-"):
        print(__doc__); sys.exit(1)
    pdf = args[0]
    dpi = int(args[args.index("--dpi") + 1]) if "--dpi" in args else 300
    show = "--show" in args
    tmp = tempfile.mkdtemp()
    subprocess.run(["pdftoppm", "-r", str(dpi), "-png", pdf, os.path.join(tmp, "p")], check=True)
    pages = sorted(glob.glob(os.path.join(tmp, "p*.png")))
    print(f"pages={len(pages)} dpi={dpi}")
    for n, img in enumerate(pages, 1):
        tsv = run(["tesseract", img, "stdout", "--psm", "6", "tsv"])
        words = [r for r in csv.DictReader(io.StringIO(tsv), delimiter="\t", quoting=csv.QUOTE_NONE)
                 if r["text"] and r["text"].strip() and float(r["conf"]) >= 0]
        if not words:
            print(f"\n=== page {n}: no text"); continue
        lines = {}
        for w in words:
            lines.setdefault((w["block_num"], w["par_num"], w["line_num"]), []).append(w)
        confs = [float(w["conf"]) for w in words]
        print(f"\n=== page {n}: words={len(words)} lines={len(lines)} meanConf={statistics.mean(confs):.0f}")
        xs = sorted(int(w["left"]) for w in words)
        cols, last = [], None
        for x in xs:
            if last is None or x - last > 60:
                cols.append([x])
            else:
                cols[-1].append(x)
            last = x
        print("column x-starts (guess):", [round(statistics.mean(c)) for c in cols if len(c) >= 5])
        for key in sorted(lines, key=lambda k: min(int(w["top"]) for w in lines[k])):
            ws = sorted(lines[key], key=lambda w: int(w["left"]))
            text = " ".join(w["text"] for w in ws)
            print(f"x={int(ws[0]['left']):4d} | {mask(text)}")
            if show:
                print(f"         {text}")

if __name__ == "__main__":
    main()
