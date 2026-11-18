import { describe, expect, it } from "vitest";
import {
  type TasteObs,
  blockedChannels,
  computeTaste,
  normalizeReasons,
  pieceSignal,
  planRun,
  seededRng,
  suggestTopics,
} from "./erleben-taste.js";

const o = (patch: Partial<TasteObs> = {}): TasteObs => ({
  kind: "scene",
  topic: "Anime",
  channel: "Kanal A",
  level: "Mittel",
  ...patch,
});
const wishes = { video: ["Anime", "Memes"], music: ["Rock"] };
const weight = (t: ReturnType<typeof computeTaste>, topic: string) =>
  t.topics.find((x) => x.topic === topic)?.weight;

describe("normalizeReasons", () => {
  it("nimmt nur bekannte Gründe, egal wie geschrieben", () => {
    expect(normalizeReasons(["Zu schwer", "zu schwer", " Kaum Russisch ", "Quatsch", 3])).toEqual([
      "zu schwer",
      "kaum russisch",
    ]);
    expect(normalizeReasons("langweilig")).toEqual([]);
  });
});

describe("Signale eines Stücks", () => {
  it("gemerkt > gut > durchgearbeitet > angesehen > nichts > weggeklickt > langweilig", () => {
    const vals = [
      pieceSignal(o({ saved: true })),
      pieceSignal(o({ rating: "up" })),
      pieceSignal(o({ worked: 1 })),
      pieceSignal(o({ watched: 1 })),
      pieceSignal(o()),
      pieceSignal(o({ skips: 2 })),
      pieceSignal(o({ rating: "down", reasons: ["langweilig"] })),
    ];
    expect([...vals].sort((a, b) => b - a)).toEqual(vals);
    // Gemerkt zählt gleich, ob mit oder ohne »gut«.
    expect(pieceSignal(o({ saved: true, rating: "up" }))).toBe(pieceSignal(o({ saved: true })));
    // Wegklicken zählt nicht, wenn das Stück später doch angesehen wurde.
    expect(pieceSignal(o({ skips: 3, watched: 1 }))).toBe(pieceSignal(o({ watched: 1 })));
  });
});

describe("Themengewichte", () => {
  it("gut/gemerkt heben, langweilig und Wegklicken senken; ohne Signale alle gleich", () => {
    expect(computeTaste([], wishes).topics.map((t) => t.weight)).toEqual([1, 1, 1]);
    const t = computeTaste(
      [
        o({ topic: "Anime", saved: true }),
        o({ topic: "Anime", rating: "up" }),
        o({ topic: "Memes", rating: "down", reasons: ["langweilig"] }),
        o({ topic: "Memes", skips: 2 }),
        o({ kind: "song", topic: "Rock", worked: 1 }),
      ],
      wishes,
    );
    expect(weight(t, "Anime")).toBeGreaterThan(weight(t, "Rock") ?? 0);
    expect(weight(t, "Rock")).toBeGreaterThan(1);
    expect(weight(t, "Memes")).toBeLessThan(0.5);
    expect(t.topics.find((x) => x.topic === "Anime")?.exposure).toBe(2);
  });

  it("ordnet Signale nur dem passenden Thema derselben Art zu", () => {
    const t = computeTaste([o({ kind: "song", topic: "anime", saved: true })], wishes);
    expect(weight(t, "Anime")).toBe(1); // Lied zu »anime« ≠ Video-Thema »Anime«
  });
});

describe("Kanalsperre", () => {
  it("einmal »nicht gut« sperrt nicht, zweimal schon; »kaum Russisch« sofort", () => {
    expect(blockedChannels([o({ rating: "down" })])).toEqual([]);
    expect(
      blockedChannels([o({ rating: "down" }), o({ channel: " kanal  a ", rating: "down" })]),
    ).toEqual(["kanal a"]);
    expect(
      blockedChannels([o({ channel: "B", rating: "down", reasons: ["kaum russisch"] })]),
    ).toEqual(["b"]);
    // »gut« oder Löschen ohne Bewertung zählen nie.
    expect(blockedChannels([o({ rating: "up" }), o(), o()])).toEqual([]);
  });
});

describe("Schwierigkeitsziel", () => {
  it("bleibt ohne Hinweise neutral", () => {
    const t = computeTaste([o()], wishes);
    expect(t).toMatchObject({ levelTarget: 1, levelKnown: false, easyWeight: 1 });
  });

  it("»zu schwer« und »wenig verstanden« ziehen nach unten, »zu leicht« und »fast alles« nach oben", () => {
    const hard = computeTaste(
      [o({ rating: "down", reasons: ["zu schwer"] }), o({ understood: 1 })],
      wishes,
    );
    expect(hard.levelTarget).toBeLessThan(0.5);
    expect(hard.easyWeight).toBeGreaterThan(1.4);
    const easy = computeTaste(
      [o({ rating: "down", reasons: ["zu leicht"] }), o({ understood: 3 })],
      wishes,
    );
    expect(easy.levelTarget).toBeGreaterThan(1.5);
    expect(easy.easyWeight).toBeLessThan(1);
  });

  it("Themen nah am Ziel kommen öfter dran", () => {
    const t = computeTaste(
      [
        o({ topic: "Anime", level: "Einsteiger" }),
        o({ topic: "Memes", level: "Anspruchsvoll" }),
        o({ topic: "Anime", level: "Einsteiger", rating: "down", reasons: ["zu leicht"] }),
      ],
      { video: ["Anime", "Memes"], music: [] },
    );
    // Ziel ≈ Mittel–schwer: Memes (anspruchsvoll) liegt näher als Anime (Einsteiger).
    expect(weight(t, "Memes")).toBeGreaterThan(weight(t, "Anime") ?? 0);
  });
});

describe("Plan eines Laufs", () => {
  const taste = computeTaste(
    [
      ...Array.from({ length: 8 }, () => o({ topic: "Anime", saved: true })),
      o({ topic: "Memes" }),
      o({ topic: "Memes" }),
    ],
    { video: ["Anime", "Memes", "Katzen"], music: [] },
  );

  it("ist mit gleichem Startwert gleich, mit anderem anders", () => {
    expect(planRun(taste, 20, seededRng(7))).toEqual(planRun(taste, 20, seededRng(7)));
    expect(planRun(taste, 20, seededRng(7))).not.toEqual(planRun(taste, 20, seededRng(8)));
  });

  it("vier von fünf Plätzen folgen dem Geschmack, jeder fünfte erkundet Unerprobtes", () => {
    const plan = planRun(taste, 100, seededRng(42));
    const explore = plan.filter((s) => s.explore);
    expect(explore).toHaveLength(20);
    expect(
      plan
        .map((s, i) => (s.explore ? i : -1))
        .filter((i) => i >= 0)
        .slice(0, 3),
    ).toEqual([4, 9, 14]);
    // Erkunden: nie das Lieblingsthema, zuerst das nie erprobte »Katzen«.
    expect(explore[0]?.topic).toBe("Katzen");
    expect(explore.every((s) => s.topic !== "Anime")).toBe(true);
    const liked = plan.filter((s) => !s.explore);
    const anime = liked.filter((s) => s.topic === "Anime").length / liked.length;
    expect(anime).toBeGreaterThan(0.6); // Anime trägt den Großteil des Gewichts
  });

  it("mit nur einem Thema erkundet es dasselbe Thema (andere Suchanfrage in prepare.py)", () => {
    const one = computeTaste([], { video: [], music: ["Rock"] });
    const plan = planRun(one, 5, seededRng(1));
    expect(plan.map((s) => s.topic)).toEqual(["Rock", "Rock", "Rock", "Rock", "Rock"]);
    expect(plan[4]?.explore).toBe(true);
  });

  it("ohne Themen gibt es keinen Plan", () => {
    expect(planRun(computeTaste([], { video: [], music: [] }), 5, seededRng(1))).toEqual([]);
  });
});

describe("Themenvorschläge", () => {
  it("schlägt Interpreten und Kanäle vor, die mehrfach gefallen — nie Vorhandenes, Abgelehntes, Gesperrtes", () => {
    const obs = [
      o({ kind: "song", topic: "Rock", channel: "KinoVEVO", by: "Кино", saved: true }),
      o({ kind: "song", topic: "Rock", channel: "Ария", by: "Ария", rating: "up" }), // nur 1 Punkt
      o({ channel: "Мосфильм", rating: "up" }),
      o({ channel: "Мосфильм", worked: 1 }),
      o({ channel: "Böse", saved: true }),
      o({ channel: "Böse", rating: "down", reasons: ["kaum russisch"] }),
    ];
    const s = suggestTopics(obs, wishes, []);
    expect(s.map((x) => [x.id, x.topic])).toEqual([
      ["scene:мосфильм", "Мосфильм"],
      ["song:кино", "Кино"],
    ]);
    expect(s[0]?.why).toBe("2 Videos von Мосфильм haben dir gefallen");
    expect(s[1]?.why).toBe("Ein Lied von Кино hat dir gefallen");
    expect(suggestTopics(obs, wishes, ["song:кино"]).map((x) => x.id)).toEqual(["scene:мосфильм"]);
    expect(
      suggestTopics(obs, { ...wishes, video: [...wishes.video, "мосфильм"] }, []).map((x) => x.id),
    ).toEqual(["song:кино"]);
  });
});
