#!/usr/bin/env python3
"""Private display subprocess contract; no fixture hooks in production."""
import atexit, json, os, pathlib, select, subprocess, sys, tempfile, time
processes=[]
def cleanup():
    for proc in processes:
        if proc.poll() is None:
            proc.kill(); proc.wait()
atexit.register(cleanup)
helper, fixture, source = sys.argv[1:]
checks = 0
def check(value, message):
    global checks
    checks += 1
    if not value: raise AssertionError(message)
def send(proc, obj):
    proc.stdin.write((json.dumps(obj)+"\n").encode()); proc.stdin.flush()
def read(proc, timeout=2):
    ready, _, _ = select.select([proc.stdout], [], [], timeout)
    check(bool(ready), "reply deadline")
    line=proc.stdout.readline()
    check(bool(line) and len(line)<=65536, "bounded complete reply")
    return json.loads(line)
def sample(g, appearance): return dict(v=1,kind="sample",generation=g,appearance=appearance)
def files(root):
    return {str(p.relative_to(root)):p.read_bytes() for p in root.rglob("*") if p.is_file()}
with tempfile.TemporaryDirectory(prefix="jasper-qt-process-") as tmp:
    root=pathlib.Path(tmp); config=root/"config"; config.mkdir(); runtime=root/"runtime";runtime.mkdir(mode=0o700)
    palette=root/"palette";palette.write_text("light")
    env=os.environ.copy()
    env.update(HOME=tmp,XDG_CONFIG_HOME=str(config),XDG_CACHE_HOME=str(root/"cache"),XDG_DATA_HOME=str(root/"data"),XDG_RUNTIME_DIR=str(runtime))
    before=files(config)
    proc=subprocess.Popen([fixture,str(palette)],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env,bufsize=0)
    processes.append(proc)
    initial=read(proc); check(initial["appearance"]=="light" and initial["source"]==source,"initial effective palette")
    pid=proc.pid
    for i,appearance in enumerate(["dark","light"]*5):
        generation=9007199254740993+i
        send(proc,sample(generation,appearance)); reply=read(proc)
        check(reply["generation"]==generation and reply["appearance"]=="light","actual polarity and exact generation")
        check(reply["window.bg"]==initial["window.bg"] and proc.pid==pid and proc.poll() is None,"one healthy unchanged process")
    # Multiple commands in one write, with the first fragmented across reads.
    command=(json.dumps(sample(80,"dark"))+"\n"+json.dumps(dict(v=1,kind="ping",generation=81))+"\n").encode()
    proc.stdin.write(command[:13]);proc.stdin.flush();time.sleep(.03);proc.stdin.write(command[13:]);proc.stdin.flush()
    check(read(proc)["generation"]==80,"fragmented sample"); check(read(proc)==dict(v=1,kind="pong",generation=81),"batched ping")
    # Exact framing limit is accepted, including harmless JSON whitespace.
    bounded=json.dumps(dict(v=1,kind="ping",generation=82)).encode()
    proc.stdin.write(bounded+b" "*(65536-len(bounded))+b"\n");proc.stdin.flush()
    check(read(proc)==dict(v=1,kind="pong",generation=82),"maximum command length accepted")
    palette.write_text("dark")
    deadline=time.monotonic()+2
    while True:
        event=read(proc,max(.01,deadline-time.monotonic()))
        check(event["generation"]==80,"event retains latest sample generation")
        if event["appearance"]=="dark": break
        check(time.monotonic()<deadline,"dark palette event deadline")
    check(proc.pid==pid and proc.poll() is None,"palette changed without restart")
    closed=subprocess.Popen([helper],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env,bufsize=0)
    processes.append(closed);read(closed);closed.stdout.close()
    send(closed,sample(90,"light"));closed.stdin.close()
    check(closed.wait(timeout=2)!=0,"closed stdout remains a failure even with simultaneous EOF")
    # The unchanged palette must refresh at the real 30-second boundary.
    started=time.monotonic()
    while True:
        periodic=read(proc,32)
        check(periodic["generation"]==80 and periodic["appearance"]=="dark","periodic refresh preserves observed state")
        if time.monotonic()-started>=27: break
    # Observe an idle interval after event work, without another toolkit startup.
    stat=pathlib.Path(f"/proc/{pid}/stat")
    if stat.exists():
        start=stat.read_text().split();time.sleep(.25);end=stat.read_text().split()
        ticks=int(end[13])+int(end[14])-int(start[13])-int(start[14])
        status=pathlib.Path(f"/proc/{pid}/status").read_text()
        rss=next(line for line in status.splitlines() if line.startswith("VmRSS:"))
        print(f"{source} idle CPU ticks/250ms={ticks}; {rss}")
    proc.stdin.close();check(proc.wait(timeout=2)==0,"prompt EOF exit")
    check(b"mapped-windows=0" in proc.stderr.read(),"zero visible helper windows")
    check(files(config)==before,"configuration unchanged")
    for payload in [b"{}\n",b"x"*65537,b'{"v":1']:
        bad=subprocess.Popen([helper],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env,bufsize=0)
        # Initial event-independent snapshot is permitted, never a partial envelope.
        processes.append(bad)
        read(bad)
        out,err=bad.communicate(payload,timeout=3)
        check(bad.returncode!=0 and len(err)<4096,"bounded invalid-command failure")
        check(not out,"no partial malformed-command output")
    for overrides in [dict(DISPLAY="",WAYLAND_DISPLAY="",QT_QPA_PLATFORM="xcb"),dict(QT_QPA_PLATFORM="jasper-missing-plugin")]:
        failed=subprocess.run([helper],input=b"",stdout=subprocess.PIPE,stderr=subprocess.PIPE,env={**env,**overrides},timeout=3)
        check(failed.returncode!=0 and not failed.stdout and len(failed.stderr)<=4096,"missing display/plugin fails boundedly")
print(f"Qt process ({source}): {checks} checks passed")
