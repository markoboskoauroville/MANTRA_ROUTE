#!/usr/bin/env python3
"""
modules/app-icon.md §3: prove it, do not eyeball it.

This computes the symbol's bounding box analytically from the geometry in
ic_launcher_foreground.xml and reports logo-widest / mask-diameter, which must land
between 0.55 and 0.60.

It is arithmetic on the path, NOT a raster of the rendered vector. A rasterised check
would also catch a path that renders differently from how it reads, and this does not.
"""
import math, sys

MASK = 72.0
BOX_MIN, BOX_MAX = 33.0, 75.0

def stroked_segment(p0, p1, width):
    """Extent of one butt-capped stroked segment."""
    (x0, y0), (x1, y1) = p0, p1
    dx, dy = x1 - x0, y1 - y0
    length = math.hypot(dx, dy)
    ux, uy = dx / length, dy / length
    px, py = -uy * width / 2, ux * width / 2   # perpendicular half-width
    corners = [
        (x0 + px, y0 + py), (x0 - px, y0 - py),
        (x1 + px, y1 + py), (x1 - px, y1 - py),
    ]
    return corners

def circle(cx, cy, r):
    return [(cx - r, cy - r), (cx + r, cy + r)]

points = []
points += stroked_segment((33.5, 54), (50, 54), 3.4)
points += stroked_segment((50, 54), (68, 41), 3.4)
points += stroked_segment((50, 54), (68, 67), 3.4)
points += circle(70.5, 39, 4.0)
points += circle(70.5, 69, 4.0)

xs = [p[0] for p in points]
ys = [p[1] for p in points]
x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
w, h = x1 - x0, y1 - y0
ratio = max(w, h) / MASK

print(f"extent    x {x0:.2f} .. {x1:.2f}   y {y0:.2f} .. {y1:.2f}")
print(f"size      {w:.2f} x {h:.2f}")
print(f"ratio     {ratio:.4f}   (target 0.55 - 0.60)")

fail = False
if not (0.55 <= ratio <= 0.60):
    print("FAIL ratio outside 0.55 - 0.60")
    fail = True
if x0 < BOX_MIN or x1 > BOX_MAX or y0 < BOX_MIN or y1 > BOX_MAX:
    print(f"FAIL something leaves the {BOX_MIN}-{BOX_MAX} symbol box")
    fail = True
print("FAIL" if fail else "PASS")
sys.exit(1 if fail else 0)
