import { describe, expect, it } from "vitest";
import { lanAddresses } from "./cast.js";

const nic = (address: string, internal = false) =>
  ({ address, family: "IPv4", internal, netmask: "", mac: "", cidr: null }) as any;

describe("lanAddresses", () => {
  it("nimmt nur private LAN-Adressen, nicht Tailscale oder Loopback", () => {
    const out = lanAddresses({
      lo0: [nic("127.0.0.1", true)],
      utun4: [nic("100.101.102.103")],
      en0: [nic("192.168.1.20")],
      en1: [nic("10.0.0.5")],
      pub: [nic("8.8.8.8")],
    } as any);
    expect(out).toEqual(["192.168.1.20", "10.0.0.5"]);
  });
});
