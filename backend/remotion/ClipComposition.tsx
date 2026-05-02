import React from "react";
import { AbsoluteFill, OffthreadVideo, useVideoConfig } from "remotion";
import { z } from "zod";

export const clipPropsSchema = z.object({
  src: z.string(),
  startSec: z.number(),
  endSec: z.number(),
  videoWidth: z.number(),
  videoHeight: z.number(),
  cropX: z.number(),
  cropY: z.number(),
  cropW: z.number(),
  cropH: z.number(),
  displayMode: z.enum(["smart-crop", "fit"]).default("smart-crop"),
});
export type ClipProps = z.infer<typeof clipPropsSchema>;

export const ClipComposition: React.FC<ClipProps> = ({
  src, startSec,
  videoWidth, videoHeight,
  cropX, cropY, cropW, cropH,
  displayMode,
}) => {
  const { fps } = useVideoConfig();
  const startFrame = Math.floor(startSec * fps);

  if (displayMode === "fit") {
    // For text-heavy slides: full 16:9 frame visible in 9:16, with a
    // blurred + scaled copy of the same frame as background. Standard
    // Instagram/YouTube-Shorts repack pattern for landscape sources.
    return (
      <AbsoluteFill style={{ backgroundColor: "black", overflow: "hidden" }}>
        {/* Background: same video, blurred + dimmed, fills the canvas */}
        <OffthreadVideo
          src={src}
          startFrom={startFrame}
          muted
          style={{
            width: "100%",
            height: "100%",
            objectFit: "cover",
            filter: "blur(40px) brightness(0.5)",
            transform: "scale(1.1)",  // hide blur edge bleed
          }}
        />
        {/* Foreground: same video, contained, full content visible */}
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
    );
  }

  // Smart-crop: object-fit cover + object-position derived from crop rect.
  const focusCenterX = cropX + cropW / 2;
  const focusCenterY = cropY + cropH / 2;
  const overPanX = videoWidth - cropW;
  const overPanY = videoHeight - cropH;
  const objectPosX = overPanX > 0 ? ((focusCenterX - cropW / 2) / overPanX) * 100 : 50;
  const objectPosY = overPanY > 0 ? ((focusCenterY - cropH / 2) / overPanY) * 100 : 50;
  const clampedX = Math.max(0, Math.min(100, objectPosX));
  const clampedY = Math.max(0, Math.min(100, objectPosY));
  return (
    <AbsoluteFill style={{ backgroundColor: "black", overflow: "hidden" }}>
      <OffthreadVideo
        src={src}
        startFrom={startFrame}
        style={{
          width: "100%",
          height: "100%",
          objectFit: "cover",
          objectPosition: `${clampedX}% ${clampedY}%`,
        }}
      />
    </AbsoluteFill>
  );
};
