// Popular servers for the Quick Play bar. Each icon is the server's own
// favicon (the one Minecraft shows in the server list), fetched by pinging it.
import { api } from "./api";
import type { PingResult } from "./types";

export interface QuickServer { name: string; address: string }

export const QUICK_SERVERS: QuickServer[] = [
  { name: "Hypixel", address: "mc.hypixel.net" },
  { name: "Minemen Club", address: "eu.minemen.club" },
  { name: "PvP Legacy", address: "pvplegacy.net" },
  { name: "MCPVP Club", address: "mcpvp.club" },
  { name: "DonutSMP", address: "donutsmp.net" },
  { name: "CubeCraft", address: "play.cubecraft.net" },
  { name: "ManaCube", address: "play.manacube.com" },
  { name: "Stray", address: "stray.gg" },
];

export const pings = $state<Record<string, PingResult | "offline" | undefined>>({});
let started = false;

/** Pings every Quick Play server once per launcher session. */
export function loadQuickPlay() {
  if (started) return;
  started = true;
  for (const s of QUICK_SERVERS) {
    api.pingServer(s.address).then((r) => { pings[s.address] = r; }).catch(() => { pings[s.address] = "offline"; });
  }
}
