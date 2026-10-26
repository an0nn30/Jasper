#!/usr/bin/env python3
"""Release artifact assertions, independent of the Gradle staging implementation."""
import argparse, os, pathlib, platform, re, stat, subprocess

NAMES = tuple("jasper-desktop-" + name for name in ("portal", "gtk3", "gtk4", "qt5", "qt6", "kde5", "kde6"))
OPTIONAL = NAMES[3:]

def output(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT)

def verify(directory, runtime="required", floor=True):
    directory = pathlib.Path(directory)
    assert sorted(p.name for p in directory.iterdir()) == sorted(NAMES), "exact seven production helpers required"
    machine = 183 if platform.machine() in ("aarch64", "arm64") else 62
    for name in NAMES:
        path = directory / name
        assert stat.S_IMODE(path.stat().st_mode) == 0o755, f"{name}: expected mode 0755"
        header = path.read_bytes()[:20]
        assert header[:6] == b"\x7fELF\x02\x01" and int.from_bytes(header[18:20], "little") == machine, f"{name}: wrong ELF architecture"
        dynamic = output("readelf", "--dynamic", "--wide", str(path))
        needed = re.findall(r"\(NEEDED\).*?\[([^]]+)\]", dynamic)
        assert not re.search(r"\((?:RPATH|RUNPATH)\)", dynamic), f"{name}: runtime search path leaks build SDK"
        assert not any(re.search(r"lib(?:gtk|adwaita)", dep, re.I) for dep in needed), f"{name}: forbidden GTK dependency"
        qt = set(re.findall(r"libQt([56])", " ".join(needed)))
        kf = set(re.findall(r"libKF([56])", " ".join(needed)))
        if name in OPTIONAL:
            assert qt == {name[-1]}, f"{name}: incorrect Qt ABI {qt}"
            assert kf == ({name[-1]} if "kde" in name else set()), f"{name}: incorrect framework ABI {kf}"
        else:
            assert not qt and not kf, f"{name}: mandatory helper links optional Qt/KF"
        if floor:
            versions = output("readelf", "--version-info", "--wide", str(path))
            assert "GLIBC_ABI_DT_RELR" not in versions and "GLIBC_PRIVATE" not in versions, f"{name}: unsupported glibc ABI requirement"
            for family, limit in (("GLIBC", (2, 35)), ("GLIBCXX", (3, 4, 30)), ("CXXABI", (1, 3, 13))):
                found = [tuple(map(int, v.split("."))) for v in re.findall(r"\b" + family + r"_([0-9.]+)", versions)]
                assert not found or max(found) <= limit, f"{name}: {family} floor exceeded: {max(found)} > {limit}"
        result = subprocess.run(["ldd", str(path)], capture_output=True, text=True)
        missing = re.findall(r"^\s*(\S+) => not found", result.stdout, re.M)
        if name not in OPTIONAL or runtime == "required":
            assert result.returncode == 0 and not missing, f"{name}: unresolved mandatory runtime: {result.stdout}{result.stderr}"
        elif missing or runtime == "missing":
            assert all(re.match(r"lib(?:Qt[56]|KF[56])", dep) for dep in missing), f"{name}: missing baseline library: {missing}"
            assert missing, f"{name}: minimal runtime unexpectedly supplies every optional dependency"
            # A loader failure is expected and bounded; a functioning helper must never be ignored.
            failed = subprocess.run([str(path)], input=b"", capture_output=True, timeout=3)
            assert failed.returncode != 0 and not failed.stdout, f"{name}: expected isolated missing-runtime startup failure"
        else:
            assert result.returncode == 0 and "not found" not in result.stdout, f"{name}: unexpected loader failure: {result.stdout}{result.stderr}"
        print(f"{name}: ELF/mode/linkage/floor verified; unresolved={missing}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("directory")
    parser.add_argument("--runtime", choices=("required", "optional", "missing"), default="required")
    parser.add_argument("--no-floor", action="store_true", help="development SDK smoke only; never release verification")
    args = parser.parse_args()
    verify(args.directory, args.runtime, not args.no_floor)
