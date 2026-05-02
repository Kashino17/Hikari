import React from "react";
import { AbsoluteFill, OffthreadVideo, useVideoConfig } from "remotion";
import { z } from "zod";

// All clips render in fit-mode: full 16:9 source visible inside the 9:16
// canvas, with a blurred + dimmed copy as background. No more smart-crop,
// no more LLM-based layout classification. Deterministic, predictable,
// debuggable. Captions are added on the Android side from clip.captions JSON.
//
// Optional inputProps fields (cropX/cropY/.../displayMode/displaySegments)
// are still accepted for backwards-compat with existing renderer call sites
// and DB rows, but completely ignored at render time.
export const clipPropsSchema = z.object({
  src: z.string(),
  startSec: z.number(),
  endSec: z.number(),
  // Legacy fields — accepted but unused. Kept so existing renderer call
  // sites compile without changes.
  videoWidth: z.number().optional(),
  videoHeight: z.number().optional(),
  cropX: z.number().optional(),
  cropY: z.number().optional(),
  cropW: z.number().optional(),
  cropH: z.number().optional(),
  displayMode: z.enum(["smart-crop", "fit"]).optional(),
  displaySegments: z.array(z.unknown()).optional(),
});
export type ClipProps = z.infer<typeof clipPropsSchema>;

export const ClipComposition: React.FC<ClipProps> = ({ src, startSec }) => {
  const { fps } = useVideoConfig();
  const startFrame = Math.floor(startSec * fps);
  return (
    <AbsoluteFill style={{ backgroundColor: "black", overflow: "hidden" }}>
      {/* Blurred + dimmed background fills the canvas */}
      <AbsoluteFill>
        <OffthreadVideo
          src={src}
          startFrom={startFrame}
          muted
          style={{
            width: "100%",
            height: "100%",
            objectFit: "cover",
            filter: "blur(40px) brightness(0.5)",
            transform: "scale(1.1)",
          }}
        />
      </AbsoluteFill>
      {/* Foreground: full 16:9 frame contained in the 9:16 canvas */}
      <AbsoluteFill style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
      }}>
        <OffthreadVideo
          src={src}
          startFrom={startFrame}
          style={{
            width: "100%",
            height: "100%",
            objectFit: "contain",
          }}
        />
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
