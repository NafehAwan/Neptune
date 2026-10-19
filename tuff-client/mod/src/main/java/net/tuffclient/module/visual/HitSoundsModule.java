package net.tuffclient.module.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.tuffclient.TuffClient;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.ChoiceSetting;
import net.tuffclient.setting.NumberSetting;

/** A sound of your choice when your hit lands. Only you hear it. */
public final class HitSoundsModule extends Module {
	private final ChoiceSetting sound = add(new ChoiceSetting("sound", "Sound", 0, "Orb", "Pling", "Bell", "Click", "Arrow", "Chime"));
	private final NumberSetting pitch = add(new NumberSetting("pitch", "Pitch", 1.2, 0.5, 2, 0.1));

	public HitSoundsModule() {
		super("hit_sounds", "Hit Sounds", "Play a sound when your hit lands. Only you hear it.", Category.VISUAL, false);
	}

	/** Called when the player attacks something (see MultiPlayerGameModeMixin). */
	public static void onAttack(Entity target) {
		HitSoundsModule m = TuffClient.modules().hitSounds;
		if (!m.isEnabled() || !(target instanceof LivingEntity)) return;
		float p = m.pitch.get().floatValue();
		SoundInstance s = switch (m.sound.get()) {
			case 1 -> SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, p);
			case 2 -> SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, p);
			case 3 -> SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, p);
			case 4 -> SimpleSoundInstance.forUI(SoundEvents.ARROW_HIT_PLAYER, p);
			case 5 -> SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME, p);
			default -> SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, p);
		};
		Minecraft.getInstance().getSoundManager().play(s);
	}
}
