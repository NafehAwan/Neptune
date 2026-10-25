"""Prints, for each Minecraft version built, the game's class list and the
signatures of every Minecraft and Fabric API class Glade uses, compressed into
the log as DUMP lines (CI only; used to port Glade between versions)."""
import base64, glob, gzip, os, re, subprocess, sys, zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HOME = os.path.expanduser("~/.gradle")


# Older names of classes Glade uses (renamed or replaced in newer versions).
EXTRA = """
net.minecraft.world.scores.Objective net.minecraft.network.chat.numbers.BlankFormat net.minecraft.client.renderer.PostChainConfig$InternalTarget
net.minecraft.network.Connection net.minecraft.client.renderer.PostChainConfig net.minecraft.client.renderer.texture.AbstractTexture
net.minecraft.client.renderer.texture.SimpleTexture net.minecraft.client.renderer.texture.ReloadableTexture net.minecraft.client.renderer.texture.TextureManager
com.mojang.blaze3d.systems.GpuDevice com.mojang.blaze3d.systems.CommandEncoder com.mojang.blaze3d.textures.GpuTexture com.mojang.blaze3d.textures.GpuTextureView
net.minecraft.client.gui.components.debug.DebugScreenEntryStatus net.minecraft.client.gui.components.debug.DebugScreenEntries
net.minecraft.client.renderer.state.level.CameraRenderState net.minecraft.client.renderer.PostPass net.minecraft.client.renderer.GraphicsResourceAllocator
net.minecraft.client.renderer.LevelTargetBundle net.minecraft.server.packs.repository.PackRepository net.minecraft.client.renderer.texture.DynamicTexture
net.minecraft.client.renderer.entity.EntityRenderDispatcher net.minecraft.world.item.ItemCooldowns net.minecraft.world.food.FoodData
net.minecraft.world.entity.item.PrimedTnt net.minecraft.client.player.KeyboardInput net.minecraft.client.player.ClientInput net.minecraft.client.player.Input
net.minecraft.world.entity.player.Input net.minecraft.world.item.PotionItem net.minecraft.world.item.SplashPotionItem net.minecraft.world.item.ThrowablePotionItem
net.minecraft.world.item.alchemy.PotionContents net.minecraft.core.component.DataComponents net.minecraft.client.renderer.Sheets
net.minecraft.client.renderer.rendertype.RenderTypes net.minecraft.client.renderer.PostChain net.minecraft.client.renderer.ShaderManager
net.minecraft.client.renderer.entity.TntRenderer net.minecraft.client.renderer.entity.state.TntRenderState net.minecraft.client.renderer.ItemBlockRenderTypes
net.minecraft.world.entity.player.Player net.minecraft.world.entity.LivingEntity net.minecraft.world.entity.Entity
net.minecraft.client.multiplayer.ClientPacketListener net.minecraft.client.renderer.debug.DebugRenderer net.minecraft.client.renderer.texture.TextureAtlas
net.minecraft.world.item.Items net.minecraft.world.effect.MobEffects net.minecraft.client.renderer.item.ItemStackRenderState net.minecraft.client.renderer.item.ItemModelResolver
com.mojang.blaze3d.pipeline.RenderTarget net.minecraft.client.gui.render.GuiRenderer net.minecraft.client.renderer.ShapeRenderer net.minecraft.client.renderer.MultiBufferSource
com.mojang.blaze3d.vertex.VertexConsumer net.minecraft.client.renderer.entity.state.HitboxesRenderState net.minecraft.client.renderer.entity.state.HitboxRenderState
net.minecraft.client.renderer.LightTexture net.minecraft.client.renderer.CubeMap net.minecraft.client.gui.components.BossHealthOverlay
net.minecraft.client.renderer.state.LevelRenderState net.minecraft.client.renderer.SubmitNodeCollector net.minecraft.client.renderer.entity.state.ItemClusterRenderState
net.minecraft.client.renderer.block.BlockRenderDispatcher net.minecraft.client.renderer.block.model.BakedQuad net.minecraft.client.renderer.chunk.SectionCompiler
net.minecraft.world.level.block.StainedGlassBlock net.minecraft.world.level.block.TransparentBlock net.minecraft.client.renderer.texture.SpriteContents
net.minecraft.client.renderer.Lightmap net.minecraft.client.renderer.fog.FogRenderer net.minecraft.client.renderer.entity.ItemFeatureRenderer
net.minecraft.client.gui.components.PlayerTabOverlay net.minecraft.client.multiplayer.PlayerInfo net.minecraft.client.gui.GuiGraphics net.minecraft.client.renderer.ItemInHandRenderer net.minecraft.resources.ResourceLocation
net.minecraft.client.renderer.ScreenEffectRenderer net.minecraft.client.renderer.LevelRenderer net.minecraft.client.renderer.GameRenderer
net.minecraft.client.renderer.FogRenderer net.minecraft.client.renderer.SkyRenderer net.minecraft.client.renderer.RenderType
net.minecraft.client.renderer.entity.ItemEntityRenderer net.minecraft.client.renderer.entity.state.ItemEntityRenderState
net.minecraft.client.renderer.entity.EntityRenderer net.minecraft.client.renderer.entity.LivingEntityRenderer
net.minecraft.client.gui.Gui net.minecraft.client.gui.components.DebugScreenOverlay net.minecraft.client.gui.screens.Screen
net.minecraft.client.gui.screens.options.OptionsScreen net.minecraft.client.gui.screens.OptionsScreen net.minecraft.client.KeyMapping
net.minecraft.client.Camera net.minecraft.client.MouseHandler net.minecraft.client.Options net.minecraft.client.multiplayer.ClientLevel
net.minecraft.client.multiplayer.MultiPlayerGameMode net.minecraft.client.player.AbstractClientPlayer net.minecraft.client.resources.PlayerSkin
net.minecraft.client.renderer.texture.OverlayTexture com.mojang.blaze3d.vertex.PoseStack com.mojang.blaze3d.platform.InputConstants
com.mojang.blaze3d.systems.RenderSystem net.minecraft.world.level.Level net.minecraft.client.gui.components.ChatComponent
net.minecraft.client.gui.Font net.minecraft.network.chat.Style net.minecraft.client.renderer.RenderPipelines
net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer
net.fabricmc.fabric.api.client.rendering.v1.LayeredDrawerWrapper net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
net.minecraft.client.gui.components.debug.DebugScreenEntryList net.minecraft.client.gui.components.DebugScreenOverlay
net.minecraft.client.resources.SkinManager net.minecraft.client.resources.PlayerSkin net.minecraft.world.entity.player.PlayerSkin
net.minecraft.network.chat.ClickEvent net.minecraft.network.chat.HoverEvent net.minecraft.client.gui.Hud net.minecraft.client.GuiMessage
net.minecraft.client.multiplayer.chat.GuiMessage net.minecraft.client.User net.minecraft.client.renderer.entity.state.EntityRenderState
net.minecraft.client.renderer.entity.state.LivingEntityRenderState net.minecraft.client.renderer.entity.state.PlayerRenderState
net.minecraft.client.renderer.entity.player.PlayerRenderer net.minecraft.client.renderer.entity.ItemRenderer
net.minecraft.client.multiplayer.ClientLevel net.minecraft.world.level.dimension.DimensionType net.minecraft.client.renderer.DimensionSpecialEffects
""".split()


# Classes whose bytecode is printed too, to see how the game uses them.
DISASM = ["net.minecraft.client.renderer.GameRenderer", "net.minecraft.client.renderer.PostChain", "net.minecraft.client.gui.components.PlayerTabOverlay"]


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
            for cls in DISASM:
                if cls.replace(".", "/") in have:
                    r = subprocess.run(["javap", "-c", "-p", "-classpath", jar, cls], capture_output=True, text=True)
                    parts.append("=== DISASM " + cls + "\n" + r.stdout)
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
