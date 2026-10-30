"""Prints, for each Minecraft version built, the game's class list and the
signatures of every Minecraft and Fabric API class Glade Bot uses, compressed into
the log as DUMP lines (CI only; used to port Glade between versions)."""
import base64, glob, gzip, os, re, subprocess, sys, zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HOME = os.path.expanduser("~/.gradle")


# Older names of classes Glade uses (renamed or replaced in newer versions).
EXTRA = """
net.minecraft.server.level.ServerPlayer net.minecraft.network.Connection net.minecraft.server.players.PlayerList
net.minecraft.server.network.CommonListenerCookie net.minecraft.server.level.ClientInformation
net.minecraft.server.network.ServerGamePacketListenerImpl net.minecraft.server.network.ServerCommonPacketListenerImpl
net.minecraft.world.entity.player.Player net.minecraft.world.entity.player.Inventory net.minecraft.world.entity.LivingEntity
net.minecraft.world.entity.Entity net.minecraft.server.level.ServerPlayerGameMode net.minecraft.world.item.alchemy.PotionContents
net.minecraft.world.item.alchemy.Potions net.minecraft.world.effect.MobEffects net.minecraft.world.item.ItemStack
net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket
net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket net.minecraft.world.level.levelgen.Heightmap
com.mojang.authlib.GameProfile net.minecraft.client.KeyMapping net.minecraft.client.gui.screens.Screen
net.minecraft.client.gui.components.Button net.minecraft.client.gui.components.StringWidget
net.minecraft.client.multiplayer.ClientPacketListener net.minecraft.commands.CommandSourceStack net.minecraft.commands.Commands
net.minecraft.core.RegistryAccess net.minecraft.core.HolderLookup net.minecraft.resources.RegistryOps
net.minecraft.world.item.enchantment.Enchantments net.minecraft.world.entity.EquipmentSlot net.minecraft.world.level.GameType
net.minecraft.world.food.FoodData net.minecraft.server.MinecraftServer net.minecraft.server.level.ServerLevel
net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
net.fabricmc.fabric.api.client.gametest.v1.context.TestClientWorldContext
""".split()


def used_classes():
    names = set(EXTRA)
    for path in glob.glob(os.path.join(ROOT, "src", "**", "*.java"), recursive=True):
        text = open(path, encoding="utf-8").read()
        names.update(re.findall(r"import\s+((?:net\.minecraft|com\.mojang|net\.fabricmc\.fabric\.api)[\w.]+);", text))
        names.update(t.replace("$", ".") for t in re.findall(r'targets\s*=\s*"([\w.$]+)"', text))
        # fully qualified uses in code, like net.minecraft.world.phys.Vec3
        names.update(re.findall(r"\b((?:net\.minecraft|com\.mojang)\.[a-z][\w.]*\.[A-Z]\w*)", text))
    return names


def jars_for(version):
    pat = re.compile(re.escape(version) + r"(?![\d.])")
    found = []
    for base in (os.path.join(HOME, "caches"), ROOT):
        for dirpath, _, files in os.walk(base):
            for f in files:
                p = os.path.join(dirpath, f)
                if f.endswith(".jar") and "sources" not in f and pat.search(p):
                    found.append(p)
    return found


def game_jar(version):
    best = None
    for p in jars_for(version):
        try:
            with zipfile.ZipFile(p) as z:
                if "net/minecraft/client/Minecraft.class" in z.namelist():
                    if best is None or os.path.getsize(p) > os.path.getsize(best):
                        best = p
        except Exception:
            pass
    return best


def fabric_jars(version):
    listing = os.path.join(ROOT, "versions", version, "build", "glade-classpath.txt")
    if os.path.exists(listing):
        return [p for p in open(listing).read().split("\n") if "fabric" in os.path.basename(p) and p.endswith(".jar")]
    out = []
    for p in glob.glob(os.path.join(HOME, "caches", "modules-2", "files-2.1", "net.fabricmc.fabric-api", "*", "*", "*", "*.jar")):
        if "sources" in p:
            continue
        if re.search(r"\+" + re.escape(version) + r"(?![\d.])", p):
            out.append(p)
    return out


def javap(jar, classes):
    out = []
    for i in range(0, len(classes), 40):
        r = subprocess.run(["javap", "-p", "-classpath", jar] + classes[i:i + 40], capture_output=True, text=True)
        out.append(r.stdout)
        if r.stderr.strip():
            out.append("ERR " + r.stderr.strip()[:2000])
    return "\n".join(out)


def class_names(jar, prefixes):
    with zipfile.ZipFile(jar) as z:
        return sorted(n[:-6] for n in z.namelist() if n.endswith(".class") and n.startswith(prefixes))


def emit(version, text):
    blob = base64.b64encode(gzip.compress(text.encode())).decode()
    chunks = [blob[i:i + 60000] for i in range(0, len(blob), 60000)]
    for i, c in enumerate(chunks):
        print(f"DUMP {version} {i} {len(chunks)} {c}")


def main():
    used = used_classes()
    for version in sys.argv[1:]:
        parts = [f"VERSION {version}"]
        jar = game_jar(version)
        parts.append(f"GAMEJAR {jar}")
        if jar:
            names = class_names(jar, ("net/minecraft/", "com/mojang/"))
            parts.append("=== CLASSES\n" + "\n".join(names))
            have = set(names)
            mc = sorted(c for c in used if not c.startswith("net.fabricmc") and c.replace(".", "/") in have)
            # inner classes of used classes too (Foo$Bar)
            inner = sorted(n.replace("/", ".") for n in names if "$" in n and n.split("$")[0].replace("/", ".") in used and not n.split("$")[1][:1].isdigit())
            parts.append("=== MISSING\n" + "\n".join(sorted(c for c in used if not c.startswith("net.fabricmc") and c.replace(".", "/") not in have)))
            parts.append("=== JAVAP\n" + javap(jar, mc + inner))
        for fj in fabric_jars(version):
            names = class_names(fj, ("net/fabricmc/fabric/api/",))
            if not names:
                continue
            parts.append(f"=== FABRIC {os.path.basename(fj)}\n" + "\n".join(names))
            wanted = [n.replace("/", ".") for n in names if n.split("$")[0].replace("/", ".") in used]
            if wanted:
                parts.append(javap(fj, wanted))
        emit(version, "\n".join(parts))


if __name__ == "__main__":
    main()
