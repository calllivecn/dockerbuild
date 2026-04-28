# -*- mode: python ; coding: utf-8 -*-

from pathlib import Path

path1 = Path('.venv/bin/certbot')
path2 = Path('ali_dns.py')


a = Analysis(
    [path1, path2],
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

exe1 = EXE(
    pyz,
    [script for script in a.scripts if script[1] == str(path1)],
    [],
    exclude_binaries=True,
    name='certbot',
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
    [script for script in a.scripts if script[1] == str(path2)],
    [],
    exclude_binaries=True,
    name='ali_dns',
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
    name='certbot',
)
