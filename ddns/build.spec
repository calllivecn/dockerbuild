# -*- mode: python ; coding: utf-8 -*-

ddns = "src/ddns.py"
ddnsclient = "src/ddnsclient.py"

a = Analysis(
    [ddns, ddnsclient],
    pathex=[],
    binaries=[],
    datas=[("getipcmd", ".")],
    hiddenimports=["pyroute2", "alibabacloud-alidns20150109"],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)

print(f"{a.scripts=}")

pyz = PYZ(a.pure)

for script in a.scripts:
	print(f"{script=}")

exe1 = EXE(
    pyz,
    [script for script in a.scripts if script[1] == ddns],
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
    [script for script in a.scripts if script[1] == ddnsclient],
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
