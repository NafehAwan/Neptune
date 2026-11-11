"""Prints the signatures of the 1.8.9 classes Glade needs (Yarn names) and the
Legacy Fabric API client classes, compressed into the log as DUMP lines (CI only)."""
import base64, glob, gzip, os, subprocess, zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACKAGES = (
    "net/minecraft/client/MinecraftClient", "net/minecraft/client/gui/", "net/minecraft/client/font/", "net/minecraft/client/option/",
    "net/minecraft/client/render/", "net/minecraft/client/util/", "net/minecraft/client/network/", "net/minecraft/client/world/",
    "net/minecraft/client/texture/", "net/minecraft/client/sound/", "net/minecraft/client/resource/", "net/minecraft/client/entity/",
    "net/minecraft/entity/", "net/minecraft/item/", "net/minecraft/util/", "net/minecraft/text/", "net/minecraft/world/World",
    "net/minecraft/inventory/", "net/minecraft/block/Block", "com/mojang/blaze3d/", "net/minecraft/client/class_",
)


def javap(jar, classes):
    out = []
    for i in range(0, len(classes), 60):
        r = subprocess.run(["javap", "-p", "-classpath", jar] + classes[i:i + 60], capture_output=True, text=True)
        out.append(r.stdout)
    return "\n".join(out)


def main():
    jars = [p for p in glob.glob(os.path.join(ROOT, ".gradle", "loom-cache", "**", "*.jar"), recursive=True) if "minecraft-merged" in p and "sources" not in p]
    jar = max(jars, key=os.path.getsize) if jars else None
    parts = [f"GAMEJAR {jar}"]
    if jar:
        with zipfile.ZipFile(jar) as z:
            names = sorted(n[:-6] for n in z.namelist() if n.endswith(".class"))
        parts.append("=== CLASSES\n" + "\n".join(n for n in names if n.startswith(("net/minecraft/", "com/mojang/"))))
        wanted = [n.replace("/", ".") for n in names if n.startswith(PACKAGES) and not n.split("$")[-1][:1].isdigit()]
        parts.append("=== JAVAP\n" + javap(jar, wanted))
    for lf in glob.glob(os.path.expanduser("~/.gradle/caches/modules-2/files-2.1/net.legacyfabric.legacy-fabric-api/*/*/*/*.jar")):
        if "sources" in lf:
            continue
        with zipfile.ZipFile(lf) as z:
            api = sorted(n[:-6].replace("/", ".") for n in z.namelist() if n.endswith(".class") and "/api/" in n and "/impl/" not in n)
        if api:
            parts.append(f"=== LFAPI {os.path.basename(lf)}\n" + javap(lf, api))
    blob = base64.b64encode(gzip.compress("\n".join(parts).encode())).decode()
    chunks = [blob[i:i + 60000] for i in range(0, len(blob), 60000)]
    for i, c in enumerate(chunks):
        print(f"DUMP 1.8.9 {i} {len(chunks)} {c}")


if __name__ == "__main__":
    main()
