// Skins for the account heads: premium accounts use their real Minecraft skin
// (fetched by the backend from Mojang), everyone else the game's default skin.
import standin from "../assets/skins/standin.png";
import { api } from "./api";
import type { Account } from "./types";

const cache = $state<Record<string, string | null>>({});
const requested = new Set<string>();
let fallback = $state<string>(standin);
let fallbackLoaded = false;

async function loadFallback() {
  if (fallbackLoaded) return;
  fallbackLoaded = true;
  // the real default skin comes from an installed Minecraft jar; until one exists, a stand-in
  const skin = await api.defaultSkin().catch(() => null);
  if (skin) fallback = skin;
}

/** The skin to draw for an account (reactive). */
export function skinOf(account: Account | null): string {
  loadFallback();
  if (!account || account.kind !== "msa") return fallback;
  const id = account.id;
  if (!requested.has(id)) {
    // fetched outside the render; the cache only changes once the skin arrives
    requested.add(id);
    api.accountSkin(id).then((s) => { if (s) cache[id] = s; }).catch(() => {});
  }
  return cache[id] ?? fallback;
}

/** The default skin (reactive), for previews when there is no account skin. */
export function fallbackSkin(): string {
  loadFallback();
  return fallback;
}
