#!/usr/bin/env python3
"""Headless helper resource observation over two real 30-second timer boundaries."""
import json, os, pathlib, select, subprocess, sys, tempfile, time

helpers = pathlib.Path(sys.argv[1]).resolve()
def observation(proc):
    assert select.select([proc.stdout], [], [], 36)[0], "periodic observation deadline"
    line = proc.stdout.readline()
    assert line and len(line) <= 65536, "bounded complete observation"
    reply = json.loads(line)
    assert reply["kind"] == "palette" and reply["generation"] == 42, reply
    return reply

def resources(proc):
    stat = pathlib.Path(f"/proc/{proc.pid}/stat").read_text().split()
    status = pathlib.Path(f"/proc/{proc.pid}/status").read_text()
    rss = next(line for line in status.splitlines() if line.startswith("VmRSS:"))
    children = pathlib.Path(f"/proc/{proc.pid}/task/{proc.pid}/children").read_text().split()
    assert proc.poll() is None and not children, "same helper alive with no child startups"
    return int(stat[13]) + int(stat[14]), rss

with tempfile.TemporaryDirectory(prefix="jasper-native-idle-") as tmp:
    root = pathlib.Path(tmp)
    config = root/"config"; config.mkdir()
    runtime = root/"runtime"; runtime.mkdir(mode=0o700)
    env = {**os.environ, "HOME":tmp, "XDG_CONFIG_HOME":str(config), "XDG_CONFIG_DIRS":str(config),
           "XDG_CACHE_HOME":str(root/"cache"), "XDG_DATA_HOME":str(root/"data"), "XDG_RUNTIME_DIR":str(runtime)}
    processes = []
    try:
        for source in ("qt5", "qt6", "kde5", "kde6"):
            proc = subprocess.Popen([str(helpers/("jasper-desktop-"+source))], stdin=subprocess.PIPE,
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env, bufsize=0)
            processes.append((source, proc))
            assert select.select([proc.stdout], [], [], 3)[0], "initial observation deadline"
            assert json.loads(proc.stdout.readline())["source"] == source
            proc.stdin.write(b'{"v":1,"kind":"sample","generation":42,"appearance":"dark"}\n'); proc.stdin.flush()
            observation(proc)
        previous = {source: resources(proc) for source, proc in processes}
        for boundary in (1, 2):
            started = time.monotonic()
            for source, proc in processes:
                observation(proc)
                while time.monotonic()-started < 27:
                    observation(proc)
                ticks, rss = resources(proc)
                print(f"{source}: boundary={boundary} pid={proc.pid} cpu_ticks_delta={ticks-previous[source][0]} {rss}; children=0", flush=True)
                previous[source] = ticks, rss
            print(f"boundary {boundary}: elapsed={time.monotonic()-started:.2f}s; helper_processes={len(processes)}; startups=4", flush=True)
        assert not list(config.iterdir()), "configuration unchanged"
        for source, proc in processes:
            proc.stdin.close(); assert proc.wait(timeout=3) == 0, f"{source} EOF"
    finally:
        for source, proc in processes:
            if proc.poll() is None: proc.kill(); proc.wait()
print("Idle helpers: 4 processes, 2 timer boundaries each, no child/repeated startups")
