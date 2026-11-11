package net.tuffclient;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Start-up test only (-Dglade.audit=true): loads every class Glade patches, so
 * a hook that doesn't fit this Minecraft version fails at start-up instead of
 * later in game. Logs "Glade hooks OK", or every failed hook and then stops.
 */
public final class HookCheck {
	private HookCheck() {
	}

	public static void runIfAsked() {
		if (!Boolean.getBoolean("glade.audit")) return;
		ClassLoader loader = HookCheck.class.getClassLoader();
		Set<String> targets = new LinkedHashSet<String>();
		List<String> failed = new ArrayList<String>();
		try {
			JsonObject config = read(loader, "tuff.mixins.json").getAsJsonObject();
			String pkg = config.get("package").getAsString();
			for (String key : new String[] {"mixins", "client"}) {
				if (!config.has(key)) continue;
				JsonArray list = config.getAsJsonArray(key);
				for (JsonElement e : list) targets.addAll(targetsOf(loader, pkg + "." + e.getAsString()));
			}
		} catch (Exception e) {
			throw new IllegalStateException("Glade hook check couldn't read its mixins", e);
		}
		for (String target : targets) {
			try {
				// loading (without running) the class applies Glade's hooks to it
				Class.forName(target, false, loader);
			} catch (Throwable t) {
				failed.add(target);
				TuffClient.LOGGER.error("Glade hook failed on {}", target, t);
			}
		}
		if (!failed.isEmpty()) throw new IllegalStateException("Glade hooks failed on " + failed);
		TuffClient.LOGGER.info("Glade hooks OK ({} classes)", targets.size());
	}

	private static JsonElement read(ClassLoader loader, String resource) throws Exception {
		InputStream in = loader.getResourceAsStream(resource);
		if (in == null) throw new IllegalStateException(resource + " not found");
		InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
		try {
			return new JsonParser().parse(reader);
		} finally {
			reader.close();
		}
	}

	/** The classes a mixin targets, from its @Mixin annotation (read as bytes: mixins can't be loaded). */
	private static List<String> targetsOf(ClassLoader loader, String mixin) throws Exception {
		final List<String> out = new ArrayList<String>();
		InputStream in = loader.getResourceAsStream(mixin.replace('.', '/') + ".class");
		if (in == null) throw new IllegalStateException(mixin + " not found");
		try {
			new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
				@Override
				public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
					if (!desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) return null;
					return new AnnotationVisitor(Opcodes.ASM9) {
						@Override
						public AnnotationVisitor visitArray(String name) {
							return new AnnotationVisitor(Opcodes.ASM9) {
								@Override
								public void visit(String n, Object value) {
									if (value instanceof Type) out.add(((Type) value).getClassName());
									else if (value instanceof String) out.add(((String) value).replace('/', '.'));
								}
							};
						}
					};
				}
			}, ClassReader.SKIP_CODE);
		} finally {
			in.close();
		}
		return out;
	}
}
