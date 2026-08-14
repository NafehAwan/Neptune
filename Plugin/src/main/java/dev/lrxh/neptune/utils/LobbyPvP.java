package dev.lrxh.neptune.utils;

import dev.lrxh.neptune.API;
import dev.lrxh.neptune.configs.impl.SettingsLocale;
import dev.lrxh.neptune.profile.data.ProfileState;
import dev.lrxh.neptune.profile.impl.Profile;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Central helper for the optional global/lobby PvP feature.
 * <p>
 * Damage between players is normally cancelled everywhere except inside a running
 * match (see {@code GlobalListener} and {@code MatchListener}). When lobby PvP is
 * enabled, those cancellers early-return for hits between two lobby players (via
 * {@link #allows(EntityDamageByEntityEvent)}) so the hit lands, and a dedicated
 * lethal handler turns a killing blow into a safe knockout (teleport to spawn,
 * no item loss) instead of a real death.
 */
public final class LobbyPvP {
    private LobbyPvP() {
    }

    public static boolean enabled() {
        return SettingsLocale.LOBBY_PVP.getBoolean();
    }

    /**
     * Resolves the attacking player behind a damage event, following projectiles
     * back to their shooter. Returns {@code null} when no player is responsible.
     */
    public static Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player direct) {
            return direct;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    /**
     * @return {@code true} only when lobby PvP is enabled and this event is a hit
     * between two distinct players who are both currently in the lobby.
     */
    public static boolean allows(EntityDamageByEntityEvent event) {
        if (!enabled()) {
            return false;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return false;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return false;
        }
        return inLobby(victim) && inLobby(attacker);
    }

    private static boolean inLobby(Player player) {
        Profile profile = API.getProfile(player);
        return profile != null && profile.getState() == ProfileState.IN_LOBBY;
    }
}
