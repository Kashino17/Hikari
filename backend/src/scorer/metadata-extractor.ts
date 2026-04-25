import Anthropic from "@anthropic-ai/sdk";

export interface ExtractedMetadata {
  seriesTitle?: string;
  season?: number;
  episode?: number;
  episodeTitle?: string;
  dubLanguage?: string;
  subLanguage?: string;
  isMovie?: boolean;
}

const EXTRACT_TOOL = {
  name: "record_metadata",
  description: "Record the extracted metadata for this video.",
  input_schema: {
    type: "object" as const,
    properties: {
      seriesTitle: { type: "string", description: "The name of the show or series (e.g. 'Breaking Bad', '3Blue1Brown')" },
      season: { type: "integer", description: "Season number" },
      episode: { type: "integer", description: "Episode number" },
      episodeTitle: { type: "string", description: "Title of the specific episode" },
      dubLanguage: { type: "string", description: "Language of the audio (if specified, e.g. 'German')" },
      subLanguage: { type: "string", description: "Language of the subtitles (if specified, e.g. 'English')" },
      isMovie: { type: "boolean", description: "Whether this is a standalone movie/video instead of a series episode" },
    },
  },
};

export interface MetadataExtractorOptions {
  apiKey: string;
  model: string;
}

export class MetadataExtractor {
  private readonly client: Anthropic;
  private readonly model: string;

  constructor(opts: MetadataExtractorOptions) {
    this.client = new Anthropic({ apiKey: opts.apiKey });
    this.model = opts.model;
  }

  async extract(title: string, description: string): Promise<ExtractedMetadata> {
    const response = await this.client.messages.create({
      model: this.model,
      max_tokens: 512,
      system: "You are a media metadata extractor. Given a video title and description, extract the series title, season, episode, and other metadata. If it's a YouTube video from a regular channel, the series title should be the channel name or the show name if it's a specific series on that channel.",
      tools: [EXTRACT_TOOL as Anthropic.Messages.Tool],
      tool_choice: { type: "tool", name: "record_metadata" },
      messages: [
        {
          role: "user",
          content: `TITLE: ${title}\n\nDESCRIPTION: ${description.slice(0, 1000)}`,
        },
      ],
    });

    const block = response.content.find((b) => b.type === "tool_use");
    if (!block || block.type !== "tool_use") {
      return {};
    }
    return block.input as ExtractedMetadata;
  }
}
