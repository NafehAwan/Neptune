"""CI: starts a 1.21.11 dedicated server with Glade Bot, runs /bot selftest
(the bot against a second bot for two minutes) and checks it plays Neth Pot
properly: no sweep hits, at least half its hits crits, real strafing, gaps,
punishes, a double pop when hit twice for lethal damage, and pots."""
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

def armour_even():
    # the first mend the bot finishes after the test wore its armour down
    worn = next((i for i, l in enumerate(lines) if "armour worn to 110" in l), None)
    m = None
    if worn is not None:
        for l in lines[worn + 1:]:
            m = re.search(r"GladeBot mended: (.*)", l)
            if m:
                break
    if not m:
        print("SELFTEST no mend after the armour was worn")
        return False
    vals = [int(v) for v in re.findall(r"=(\d+)/\d+", m.group(1))]
    print(f"SELFTEST armour after mending (durability points) {vals}")
    # every piece back to the same points (within a few XP orbs), well above the worn 110
    return len(vals) == 4 and max(vals) - min(vals) <= 30 and min(vals) >= 180

def stats(text):
    return {k: int(v) for k, v in re.findall(r"(\w+)=(\d+)", text)}

if wait_for(r"Done \(", 600):
    send("bot selftest")
    m = wait_for(r"\[gladebot\] result winner=(\w+) (.*)", 400)
    sp = wait_for(r"\[gladebot\] sparring (hits=.*)", 30)
    if m:
        bot = stats(m.group(2))
        other = stats(sp.group(1)) if sp else {}
        both = {k: bot.get(k, 0) + other.get(k, 0) for k in set(bot) | set(other)}
        print(f"SELFTEST winner={m.group(1)}")
        print(f"SELFTEST bot      {bot}")
        print(f"SELFTEST sparring {other}")
        checks = {
            "no sweep hits": both.get("sweeps", 1) == 0,
            "at least half the hits are crits": both.get("hits", 0) > 0 and both.get("crits", 0) * 2 >= both.get("hits", 0),
            "strafes (10+ direction changes)": both.get("strafeFlips", 0) >= 10,
            "really moves sideways (15+ blocks each)": bot.get("strafeBlocks", 0) >= 15 and other.get("strafeBlocks", 0) >= 15,
            "gaps": both.get("gaps", 0) >= 1,
            "punishes": both.get("punishes", 0) >= 1,
            "double pop when hit twice": bot.get("doublePops", 0) >= 1,
            "pots": both.get("pots", 0) > 0,
            "jump resets": both.get("jumpResets", 0) >= 1,
            "playstyle changes": both.get("styleSwitches", 0) >= 1,
            "mends": bot.get("mends", 0) >= 1,
            "armour mended evenly": armour_even(),
        }
        # aim trainer: a bot aiming perfectly at a moving target scores high, and the target really moves
        send("bot aimselftest")
        am = wait_for(r"\[gladebot\] aim result accuracy=(\d+) moved=(\d+)", 90)
        if am:
            print(f"SELFTEST aim trainer accuracy={am.group(1)}% target moved={am.group(2)} blocks")
        checks["aim trainer scores a perfect aimer (85%+)"] = bool(am) and int(am.group(1)) >= 85
        checks["aim trainer target moves (5+ blocks)"] = bool(am) and int(am.group(2)) >= 5
        for name, passed in checks.items():
            print(f"SELFTEST {'PASS' if passed else 'FAIL'} {name}")
        ok = all(checks.values())
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
