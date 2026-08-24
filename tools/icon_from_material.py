#!/usr/bin/env python3
"""
Convert an official Material Symbols SVG (viewBox "0 -960 960 960") into an Android
vector drawable in exactly the form TTT_MINI uses: viewport 960, one filled path,
positive y. The conversion is y -> y + 960, which is what TTT_MINI's own icons show
(its check mark is M382,720 where Google's is M382-240; -240 + 960 = 720).
"""
import re, sys, os

def shift(d):
    out=[]; i=0
    # tokenise into commands and numbers, then add 960 to every y coordinate
    tok=re.findall(r'[A-Za-z]|-?\d*\.?\d+', d)
    cmd=None; buf=[]
    # pairs-per-command for the commands Material Symbols actually emits
    arity={'M':2,'m':2,'L':2,'l':2,'H':1,'h':1,'V':1,'v':1,'Z':0,'z':0,
           'C':6,'c':6,'S':4,'s':4,'Q':4,'q':4,'T':2,'t':2,'A':7,'a':7}
    absolute=True
    res=[]
    idx=0
    while idx < len(tok):
        t=tok[idx]
        if re.match(r'[A-Za-z]', t):
            cmd=t; absolute=cmd.isupper(); res.append(cmd); idx+=1
            continue
        n=arity.get(cmd,2)
        nums=[float(x) for x in tok[idx:idx+n]]
        idx+=n
        if cmd in ('M','L','C','S','Q','T'):
            for k in range(1,len(nums),2): nums[k]+=960
        elif cmd=='V':
            nums[0]+=960
        elif cmd=='A':
            nums[6]+=960
        res.append(",".join(("%g"%x) for x in nums))
    return "".join(
        (r if re.match(r'[A-Za-z]', r) else r) for r in res
    )

def build(name, src, dst):
    svg=open(src).read()
    m=re.search(r'<path[^>]*\bd="([^"]+)"', svg)
    if not m: raise SystemExit(f"no path in {src}")
    d=shift(m.group(1))
    xml=('<vector xmlns:android="http://schemas.android.com/apk/res/android" '
         'android:height="24dp" android:tint="#F2DDB4" '
         'android:viewportHeight="960" android:viewportWidth="960" android:width="24dp">\n'
         '    <path android:fillColor="@android:color/white" android:pathData="'+d+'"/>\n'
         '</vector>\n')
    open(dst,'w').write(xml)
    return len(d)

for name,out in [("speaker","ic_out_speaker"),("headphones","ic_out_earpiece"),
                 ("headset_mic","ic_out_wired"),("usb","ic_out_usb"),
                 ("bluetooth","ic_out_bluetooth"),("bluetooth_searching","ic_out_ble"),
                 ("hearing_aid","ic_out_hearing"),("settings_input_hdmi","ic_out_hdmi"),
                 ("dock","ic_out_dock"),("cast","ic_out_cast"),
                 ("volume_up","ic_notification")]:
    n=build(name, f"{name}.svg", f"/home/claude/MANTRA_ROUTE/app/src/main/res/drawable/{out}.xml")
    print(f"  {out:20s} <- {name:22s} path {n} chars")
