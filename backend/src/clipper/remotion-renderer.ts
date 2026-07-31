import { stat } from "node:fs/promises";
import { basename, dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { bundle } from "@remotion/bundler";
import { renderMedia, selectComposition } from "@remotion/renderer";
import { computeCropRect } from "./crop-math.js";
import type { ClipSpec } from "./qwen-analyzer.js";

export interface RenderInput {
  inputPath: string;
  videoWidth: number;
  videoHeight: number;
  spec: ClipSpec;
  outputPath: string;
}

// Remotion's OffthreadVideo requires http(s):// URLs — file:// is rejected by
// its asset-downloader. The Hikari backend already serves the video files at
// /videos/<id>.mp4 on the configured port, so we route through that.
const BACKEND_PORT = process.env.PORT ?? "3939";
function backendUrlFor(filePath: string): string {
  const filename = basename(filePath);
  return `http://localhost:${BACKEND_PORT}/videos/${encodeURIComponent(filename)}`;
}
export interface RenderResult {
  filePath: string;
  sizeBytes: number;
}

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const REMOTION_ENTRY = resolve(__dirname, "../../remotion/index.ts");

let bundlePromise: Promise<string> | null = null;
function getBundle(): Promise<string> {
  bundlePromise ??= bundle({ entryPoint: REMOTION_ENTRY });
  return bundlePromise;
}

export async function renderClip(input: RenderInput): Promise<RenderResult> {
  const { inputPath, videoWidth, videoHeight, spec, outputPath } = input;
  const crop = computeCropRect({
    videoWidth, videoHeight,
    focus: spec.focus,
    targetAspect: 9 / 16,
  });

  const inputProps = {
    src: backendUrlFor(inputPath),
    startSec: spec.startSec,
    endSec: spec.endSec,
    videoWidth, videoHeight,
    cropX: crop.x, cropY: crop.y, cropW: crop.w, cropH: crop.h,
    displayMode: spec.displayMode,
    displaySegments: spec.displaySegments ?? [],  // composition handles empty/non-empty
  };

  const serveUrl = await getBundle();
  const composition = await selectComposition({
    serveUrl,
    id: "Clip",
    inputProps,
  });

  await renderMedia({
    composition,
    serveUrl,
    codec: "h264",
    outputLocation: outputPath,
    inputProps,
  });

  const stats = await stat(outputPath);
  return { filePath: outputPath, sizeBytes: stats.size };
}
