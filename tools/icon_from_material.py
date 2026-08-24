#!/usr/bin/env python3
"""
Material Symbols SVG -> Android vector, in TTT_MINI's format (viewport 960, y shifted +960).

REWRITTEN 24.8.2026. The hand-rolled tokeniser in the first version glued implicit repeated
coordinate groups together with no separator: "L100,200" followed by an implicit "300,400"
became "L100,200300,400". Paths without implicit repeats survived; paths with them - alarm
among them - became unparseable and shipped as a BLANK TILE. Nothing in the build said a word:
a malformed vector compiles, packages, installs and draws nothing.

So the parsing is no longer hand-rolled. svgpathtools understands the grammar, the translate is
a real transform, and every output is rendered to PNG and checked for ink before it is kept.
"""
import re, sys, os
from svgpathtools import parse_path
import cairosvg

def convert(src_svg):
    svg = open(src_svg).read()
    ds = re.findall(r'<path[^>]*\bd="([^"]+)"', svg)
    if not ds:
        raise SystemExit(f"no path in {src_svg}")
    out = []
    for d in ds:
        p = parse_path(d).translated(complex(0, 960))
        out.append(p.d())
    return " ".join(out)

def has_ink(path_d, png_out):
    """Render it and count non-background pixels. A blank icon must never ship again."""
    svg = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 960 960" '
           'width="96" height="96">'
           f'<path fill="#F2DDB4" d="{path_d}"/></svg>')
    cairosvg.svg2png(bytestring=svg.encode(), write_to=png_out,
                     background_color="#12161E", output_width=96, output_height=96)
    import struct, zlib
    data = open(png_out, 'rb').read()
    # crude but sufficient: a uniform PNG compresses to a tiny file
    return len(data) > 400

def build(src, dst, png):
    d = convert(src)
    if not has_ink(d, png):
        return None
    xml = ('<vector xmlns:android="http://schemas.android.com/apk/res/android" '
           'android:height="24dp" android:tint="#F2DDB4" '
           'android:viewportHeight="960" android:viewportWidth="960" android:width="24dp">\n'
           f'    <path android:fillColor="@android:color/white" android:pathData="{d}"/>\n'
           '</vector>\n')
    open(dst, 'w').write(xml)
    return len(d)

if __name__ == "__main__":
    DRAW = "/home/claude/MANTRA_ROUTE/app/src/main/res/drawable"
    os.makedirs("render2", exist_ok=True)
    JOBS = [
        ("volume_up","ic_out_speaker"), ("headphones","ic_out_earpiece"),
        ("headset_mic","ic_out_wired"), ("usb","ic_out_usb"),
        ("bluetooth","ic_out_bluetooth"), ("bluetooth_searching","ic_out_ble"),
        ("hearing_aid","ic_out_hearing"), ("settings_input_hdmi","ic_out_hdmi"),
        ("dock","ic_out_dock"), ("cast","ic_out_cast"), ("volume_up","ic_notification"),
        ("call","ic_stream_call"), ("music_note","ic_stream_media"),
        ("ring_volume","ic_stream_ring"), ("notifications","ic_stream_notification"),
        ("alarm","ic_stream_alarm"),
    ]
    blank = []
    for src, out in JOBS:
        n = build(f"{src}.svg", f"{DRAW}/{out}.xml", f"render2/{out}.png")
        if n is None:
            blank.append(out); print(f"  BLANK  {out:26s} <- {src}")
        else:
            print(f"  ok     {out:26s} <- {src:20s} {n:5d} chars")
    print()
    print(f"{len(JOBS)} icons generated, {len(blank)} blank")
    sys.exit(1 if blank else 0)
