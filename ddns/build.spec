# -*- mode: python ; coding: utf-8 -*-

ddns = 'src/ddns.py'
ddnsclient = 'src/ddnsclient.py'

a = Analysis(
    [ddns, ddnsclient],
    pathex=[],
    binaries=[],
    datas=[],
    hiddenimports=[],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

#print("==================================")
#for s in a.scripts:
#	print(f"{s}")
#exit(0)

exe1 = EXE(
    pyz,
    [script for script in a.scripts if ddns in script[1]],
    [],
    exclude_binaries=True,
    name='ddns',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=True,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)

exe2 = EXE(
    pyz,
    [script for script in a.scripts if ddnsclient in script[1]],
    [],
    exclude_binaries=True,
    name='ddnsclient',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=True,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)

coll = COLLECT(
    exe1, exe2,
    a.binaries,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name='ddns',
)
