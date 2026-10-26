#!/usr/bin/env python3
"""KConfig cascade/reparse and shared Qt event-loop contracts on a private display."""
import atexit, json, os, pathlib, re, select, subprocess, sys, tempfile, time

helper, fixture, generic, source, fixtures = sys.argv[1:]
fixtures = pathlib.Path(fixtures)
checks = 0
processes = []
def cleanup():
    for proc in processes:
        if proc.poll() is None:
            proc.kill(); proc.wait()
atexit.register(cleanup)
def check(value, message):
    global checks
    checks += 1
    if not value: raise AssertionError(message)
def read(proc, timeout=2):
    check(bool(select.select([proc.stdout], [], [], timeout)[0]), "reply deadline")
    line = proc.stdout.readline()
    check(bool(line) and len(line) <= 65536, "bounded complete reply")
    return json.loads(line)
def send(proc, generation, appearance="dark"):
    proc.stdin.write((json.dumps(dict(v=1, kind="sample", generation=generation, appearance=appearance))+"\n").encode())
    proc.stdin.flush()
def start(command, env):
    proc = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env, bufsize=0)
    processes.append(proc)
    return proc
def contents(root):
    return {str(p.relative_to(root)): p.read_bytes() for p in root.rglob("*") if p.is_file()}
def rgb(value, r, g, b):
    value = [float(component) for component in value.split(",")]
    return len(value) == 4 and all(abs(a-e/255) < .00002 for a,e in zip(value[:3], [r,g,b]))
def isolation(proc, kde=True):
    maps = pathlib.Path(f"/proc/{proc.pid}/maps").read_text()
    major = source[-1]
    check(set(re.findall(r"libQt([56])", maps)) == {major}, "exactly one intended Qt major mapped")
    check(not re.search(r"lib(gtk|adwaita)", maps), "no GTK/libadwaita mapped")
    if kde:
        check(f"libKF{major}" in maps and f"libKF{11-int(major)}" not in maps, "matching KDE framework only")
    else:
        check("libKF" not in maps, "generic Qt loads no KDE framework")

with tempfile.TemporaryDirectory(prefix="jasper-kde-process-") as tmp:
    root = pathlib.Path(tmp)
    config = root/"config"; config.mkdir()
    system = root/"system"; system.mkdir()
    runtime = root/"runtime"; runtime.mkdir(mode=0o700)
    control = root/"control"; control.write_text("event")
    globals = config/"kdeglobals"
    (system/"kdeglobals").write_bytes((fixtures/"distinct.colors").read_bytes())
    globals.write_text("[Colors:Window]\nBackgroundNormal=61,62,63\nForegroundNormal=201,202,203\n")
    env = {**os.environ, "HOME":tmp, "XDG_CONFIG_HOME":str(config), "XDG_CONFIG_DIRS":str(system),
           "XDG_CACHE_HOME":str(root/"cache"), "XDG_DATA_HOME":str(root/"data"), "XDG_RUNTIME_DIR":str(runtime)}
    before_system = contents(system)
    proc = start([fixture, str(control)], env)
    initial = read(proc)
    check(initial["source"] == source and initial["appearance"] == "dark", "source and actual initial polarity")
    check(rgb(initial["window.bg"],61,62,63) and rgb(initial["view.bg"],21,22,23), "user override and inherited system pair")
    check(initial["dialog.bg"] == initial["window.bg"] and "sidebar.bg" not in initial and "header.bg" not in initial, "paired mapping and absent unsupported roles")
    check("KF built against " in initial["libraryVersion"] and " / Qt runtime " in initial["libraryVersion"], "truthful compiled KF and runtime Qt metadata")
    isolation(proc)
    for generation, appearance in enumerate(["light", "dark"]*5, 9007199254740993):
        send(proc, generation, appearance)
        reply = read(proc)
        check(reply["generation"] == generation and reply["appearance"] == "dark", "healthy mismatch preserves actual polarity and generation")
    # Event delivery triggers a genuine KConfig reparse in the shared helper loop.
    for generation, mode in enumerate(["event", "palette", "style"], 100):
        globals.write_text(f"[Colors:Window]\nBackgroundNormal=255,255,{generation}\nForegroundNormal=0,0,0\n")
        control.write_text(mode)
        reply = read(proc)
        check(reply["appearance"] == "light" and rgb(reply["window.bg"],255,255,generation), f"{mode} reparses live config")
    control.write_text("missing")
    missing = read(proc)
    check(missing["appearance"] == "unknown" and "window.bg" not in missing and missing["reason"], "unavailable Window is a complete healthy observation")
    for generation in [200,201]:
        send(proc, generation, "light")
        reply = read(proc)
        check(reply["generation"] == generation and reply["appearance"] == "unknown" and proc.poll() is None, "missing pair replies satisfy deadline without restart")
    control.write_text("event"); recovered = read(proc)
    check(recovered["appearance"] == "light" and recovered["generation"] == 201, "same process recovers on event")
    # Explicit samples also reparse, with no synthetic event.
    globals.write_bytes((fixtures/"header.colors").read_bytes())
    send(proc, 202, "light"); explicit = read(proc)
    check(explicit["appearance"] == "dark" and rgb(explicit["window.bg"],61,62,63), "explicit request reparses config with actual polarity")
    # A real 30s timer boundary must refresh changed config without any request/event.
    globals.write_bytes((fixtures/"high-contrast.colors").read_bytes())
    periodic = read(proc, 32)
    check(periodic["generation"] == 202 and periodic["appearance"] == "light" and rgb(periodic["window.bg"],255,255,255), "periodic sample reparses config")
    before = contents(config)
    proc.stdin.close(); check(proc.wait(timeout=2) == 0, "timely EOF")
    check(b"mapped-windows=0 activated-windows=0" in proc.stderr.read(), "no helper windows shown or activated")
    check(contents(config) == before and contents(system) == before_system, "helper never writes configuration")
    # Production binary, rather than the fixture, obeys the same source isolation.
    real = start([helper], env); read(real); isolation(real)
    real.stdin.close(); check(real.wait(timeout=2) == 0, "production EOF")
    # Remove one required KF SONAME from an isolated executable copy. This is a
    # missing ELF dependency, without changing installed libraries or the host.
    binary = pathlib.Path(helper).read_bytes()
    soname = ("libKF5ConfigWidgets.so.5" if source == "kde5" else "libKF6ColorScheme.so.6").encode()
    check(soname+b"\0" in binary, "matching optional ELF dependency")
    broken = root/"missing-kf"; broken.write_bytes(binary.replace(soname, soname.replace(b"libKF", b"libJX"))); broken.chmod(0o755)
    failed = subprocess.run([str(broken)], input=b"", stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env, timeout=3)
    check(failed.returncode != 0 and not failed.stdout and b"libJX" in failed.stderr, "missing KF fails only KDE startup")
    sibling = start([generic], env); qt = read(sibling); isolation(sibling, False)
    check(qt["source"] == "qt"+source[-1], "generic sibling remains available")
    globals.write_bytes((fixtures/"distinct.colors").read_bytes())
    send(sibling, 300); unchanged = read(sibling)
    check(unchanged["window.bg"] == qt["window.bg"] and unchanged["view.bg"] == qt["view.bg"], "generic Qt never imports changed KDE configuration")
    sibling.stdin.close(); check(sibling.wait(timeout=2) == 0, "generic sibling EOF")
print(f"KDE process ({source}): {checks} checks passed")
