import { networkInterfaces } from "node:os";
import type { FastifyInstance } from "fastify";

/** 100.64.0.0/10 = Tailscale/CGNAT: vom Fernseher im WLAN nicht erreichbar. */
function isCgnat(ip: string): boolean {
  const [a = 0, b = 0] = ip.split(".").map(Number);
  return a === 100 && b >= 64 && b <= 127;
}

function isPrivateLan(ip: string): boolean {
  const [a = 0, b = 0] = ip.split(".").map(Number);
  return a === 10 || (a === 192 && b === 168) || (a === 172 && b >= 16 && b <= 31);
}

/**
 * IPv4-Adressen dieses Rechners im lokalen Netz — nur darüber erreicht ein
 * Chromecast/Smart-TV den Server. Die Tailnet-Adresse taugt dafür nicht.
 */
export function lanAddresses(
  ifaces: ReturnType<typeof networkInterfaces> = networkInterfaces(),
): string[] {
  const out: string[] = [];
  for (const list of Object.values(ifaces)) {
    for (const i of list ?? []) {
      if (i.family !== "IPv4" || i.internal) continue;
      if (isCgnat(i.address) || !isPrivateLan(i.address)) continue;
      out.push(i.address);
    }
  }
  return out;
}

export function registerCastRoutes(app: FastifyInstance, port: number): void {
  app.get("/cast/info", async () => ({
    lanBaseUrls: lanAddresses().map((ip) => `http://${ip}:${port}`),
  }));
}
