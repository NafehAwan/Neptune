// Skins for the account heads and the 3D player: premium accounts use their real
// Minecraft skin (fetched by the backend from Mojang), everyone else Steve.
import steve from "../assets/glade/steve.png";
import { api } from "./api";
import type { Account } from "./types";

const cache = $state<Record<string, string | null>>({});
const requested = new Set<string>();
const fallback = steve;

/** The skin to draw for an account (reactive). */
export function skinOf(account: Account | null): string {
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
  return fallback;
}
