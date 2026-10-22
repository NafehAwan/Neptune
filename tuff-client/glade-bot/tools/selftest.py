"""CI: starts a 1.21.11 dedicated server with Glade Bot, runs /bot selftest
(the bot against a dummy that stands still) and checks the bot fought:
it landed hits, threw pots, swapped a totem in when low, and won."""
import os, re, subprocess, sys, threading, time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RUN = os.path.join(ROOT, "versions", "1.21.11", "run")
os.makedirs(RUN, exist_ok=True)
open(os.path.join(RUN, "eula.txt"), "w").write("eula=true\n")
open(os.path.join(RUN, "server.properties"), "w").write("\n".join([
    "online-mode=false", "level-type=minecraft\\:flat", "generate-structures=false", "spawn-protection=0",
    "view-distance=4", "simulation-distance=4", "difficulty=easy", "spawn-monsters=false", "spawn-animals=false",
]) + "\n")

p = subprocess.Popen(["./gradlew", "--no-daemon", "--console=plain", ":1.21.11:runServer"], cwd=ROOT,
                     stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
lines = []
def read():
    for line in p.stdout:
        lines.append(line)
        if "[gladebot]" in line or "Exception" in line or "ERROR" in line or "Done (" in line or "FAIL" in line or "error:" in line:
            print(line, end="", flush=True)
threading.Thread(target=read, daemon=True).start()

def wait_for(pattern, timeout):
    end = time.time() + timeout
    while time.time() < end:
        for l in list(lines):
            m = re.search(pattern, l)
            if m:
                return m
        if p.poll() is not None:
            return None
        time.sleep(1)
    return None

def send(cmd):
    p.stdin.write(cmd + "\n")
    p.stdin.flush()

ok = False
if wait_for(r"Done \(", 600):
    send("bot selftest")
    m = wait_for(r"\[gladebot\] result winner=(\w+) hits=(\d+) pots=(\d+) totemSwaps=(\d+)", 300)
    if m:
        winner, hits, pots, swaps = m.group(1), int(m.group(2)), int(m.group(3)), int(m.group(4))
        print(f"SELFTEST winner={winner} hits={hits} pots={pots} totemSwaps={swaps}")
        ok = winner == "bot" and hits > 0 and pots > 0 and swaps > 0
    else:
        print("SELFTEST no result")
    send("stop")
else:
    print("SELFTEST server didn't start")
try:
    p.wait(timeout=90)
except subprocess.TimeoutExpired:
    p.kill()
if not ok:
    print("".join(lines[-80:]))
sys.exit(0 if ok else 1)
