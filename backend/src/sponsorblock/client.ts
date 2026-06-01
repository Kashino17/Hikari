export interface SponsorSegment {
  category: string;
  startSeconds: number;
  endSeconds: number;
}

interface ApiSegment {
  category: string;
  segment: [number, number];
}

/**
 * Fetches SponsorBlock segments for a video.
 *
 * Returns `[]` ONLY when the API confirms there are no segments (404), and
 * `null` when the lookup FAILED (network error, 5xx, malformed JSON). The
 * caller must distinguish these: persisting `[]` is a real "no segments here"
 * fact, but a failure must NOT be written — otherwise a transient blip would
 * stamp "no segments" and the enhancement would be lost until a manual re-sync.
 */
export async function fetchSponsorSegments(videoId: string): Promise<SponsorSegment[] | null> {
  const url = `https://sponsor.ajay.pw/api/skipSegments?videoID=${encodeURIComponent(videoId)}`;
  try {
    const res = await fetch(url);
    if (res.status === 404) return []; // confirmed: this video has no segments
    if (!res.ok) return null; // 5xx / rate-limit / unexpected → failure, don't persist
    const data = (await res.json()) as ApiSegment[];
    return data.map((s) => ({
      category: s.category,
      startSeconds: s.segment[0],
      endSeconds: s.segment[1],
    }));
  } catch {
    // DNS failure, network offline, or malformed JSON: a FAILURE, not "empty".
    // Best-effort enhancement, never a pipeline blocker — caller skips the write.
    return null;
  }
}
