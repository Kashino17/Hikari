export interface SponsorSegment {
  category: string;
  startSeconds: number;
  endSeconds: number;
}

interface ApiSegment {
  category: string;
  segment: [number, number];
}

export async function fetchSponsorSegments(videoId: string): Promise<SponsorSegment[]> {
  const url = `https://sponsor.ajay.pw/api/skipSegments?videoID=${encodeURIComponent(videoId)}`;
  try {
    const res = await fetch(url);
    if (res.status === 404 || !res.ok) return [];
    const data = (await res.json()) as ApiSegment[];
    return data.map((s) => ({
      category: s.category,
      startSeconds: s.segment[0],
      endSeconds: s.segment[1],
    }));
  } catch {
    // DNS failure, network offline, or malformed JSON: treat as "no segments".
    // SponsorBlock is a best-effort enhancement, never a pipeline blocker.
    return [];
  }
}
