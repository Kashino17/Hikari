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
});
export type ClipProps = z.infer<typeof clipPropsSchema>;

export const ClipComposition: React.FC<ClipProps> = ({
  src, startSec,
  videoWidth, videoHeight,
  cropX, cropY, cropW, cropH,
}) => {
  const { fps } = useVideoConfig();
  // Smart-crop via CSS object-fit/object-position. The crop rect is
  // (cropX, cropY, cropW, cropH) in source-pixel coords. We translate that
  // into an object-position percentage that aligns the crop center with the
  // container center.
  //
  //   focusCenter / (sourceSize - cropSize) ≈ object-position percentage
  //
  // (the denominator is the over-pan range, so we map focus center to that).
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
        startFrom={Math.floor(startSec * fps)}
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
