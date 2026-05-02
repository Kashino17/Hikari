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
  const { fps, width: outW, height: outH } = useVideoConfig();
  const scale = Math.max(outW / cropW, outH / cropH);
  const translateX = -(cropX + cropW / 2) * scale + outW / 2;
  const translateY = -(cropY + cropH / 2) * scale + outH / 2;
  return (
    <AbsoluteFill style={{ backgroundColor: "black", overflow: "hidden" }}>
      <div
        style={{
          position: "absolute",
          width: videoWidth,
          height: videoHeight,
          transform: `translate(${translateX}px, ${translateY}px) scale(${scale})`,
          transformOrigin: "0 0",
        }}
      >
        <OffthreadVideo src={src} startFrom={Math.floor(startSec * fps)} />
      </div>
    </AbsoluteFill>
  );
};
