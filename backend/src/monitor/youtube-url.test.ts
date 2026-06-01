import { describe, expect, it } from "vitest";
import { validateYouTubeChannelUrl } from "./youtube-url.js";

describe("validateYouTubeChannelUrl — accepts real channel shapes", () => {
  it("accepts /channel/UC…", () => {
    const out = validateYouTubeChannelUrl("https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv");
    expect(out).toBe("https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv");
  });

  it("accepts /@handle and normalizes host", () => {
    expect(validateYouTubeChannelUrl("https://youtube.com/@finanzfluss")).toBe(
      "https://www.youtube.com/@finanzfluss",
    );
  });

  it("accepts /c/Name and /user/Name", () => {
    expect(validateYouTubeChannelUrl("https://www.youtube.com/c/Kurzgesagt")).toBe(
      "https://www.youtube.com/c/Kurzgesagt",
    );
    expect(validateYouTubeChannelUrl("https://www.youtube.com/user/vsauce")).toBe(
      "https://www.youtube.com/user/vsauce",
    );
  });

  it("accepts a bare vanity path", () => {
    expect(validateYouTubeChannelUrl("https://www.youtube.com/veritasium")).toBe(
      "https://www.youtube.com/veritasium",
    );
  });

  it("accepts m. and music. subdomains", () => {
    expect(validateYouTubeChannelUrl("https://m.youtube.com/@x_y")).toBe(
      "https://www.youtube.com/@x_y",
    );
  });
});

describe("validateYouTubeChannelUrl — rejects SSRF / non-YouTube", () => {
  it("rejects non-https", () => {
    expect(validateYouTubeChannelUrl("http://www.youtube.com/@x_y")).toBeNull();
  });

  it("rejects file:// and other schemes", () => {
    expect(validateYouTubeChannelUrl("file:///etc/passwd")).toBeNull();
    expect(validateYouTubeChannelUrl("ftp://youtube.com/@x")).toBeNull();
  });

  it("rejects the cloud metadata endpoint", () => {
    expect(validateYouTubeChannelUrl("https://169.254.169.254/latest/meta-data/")).toBeNull();
  });

  it("rejects look-alike / non-allowlisted hosts", () => {
    expect(validateYouTubeChannelUrl("https://youtube.com.evil.com/@x")).toBeNull();
    expect(validateYouTubeChannelUrl("https://notyoutube.com/@x")).toBeNull();
    expect(validateYouTubeChannelUrl("https://evil.com/@x")).toBeNull();
  });

  it("rejects /watch, /playlist, /shorts and other non-channel paths", () => {
    expect(validateYouTubeChannelUrl("https://www.youtube.com/watch?v=abc")).toBeNull();
    expect(validateYouTubeChannelUrl("https://www.youtube.com/playlist?list=PL")).toBeNull();
    expect(validateYouTubeChannelUrl("https://www.youtube.com/shorts")).toBeNull();
  });

  it("rejects empty, whitespace, and garbage", () => {
    expect(validateYouTubeChannelUrl("")).toBeNull();
    expect(validateYouTubeChannelUrl("   ")).toBeNull();
    expect(validateYouTubeChannelUrl("not a url")).toBeNull();
    expect(validateYouTubeChannelUrl("https://www.youtube.com/")).toBeNull();
  });

  it("rejects a malformed channel id", () => {
    expect(validateYouTubeChannelUrl("https://www.youtube.com/channel/notarealid")).toBeNull();
  });
});
