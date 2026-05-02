import React from "react";
import { AbsoluteFill, OffthreadVideo, Sequence, useVideoConfig } from "remotion";
import { z } from "zod";

export const segmentSchema = z.object({
  startSec: z.number(),
  endSec: z.number(),
  mode: z.enum(["smart-crop", "fit"]),
  focus: z.object({
    x: z.number(), y: z.number(), w: z.number(), h: z.number(),
  }).optional(),
});

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
  displaySegments: z.array(segmentSchema).default([]),
});
export type ClipProps = z.infer<typeof clipPropsSchema>;
export type SegmentProps = z.infer<typeof segmentSchema>;

// FitLayer: full 16:9 frame letterboxed in 9:16 with blurred background.
const FitLayer: React.FC<{
  src: string;
  sourceStartFrame: number;
}> = ({ src, sourceStartFrame }) => (
  <AbsoluteFill style={{ backgroundColor: "black", overflow: "hidden" }}>
    <AbsoluteFill>
      <OffthreadVideo
        src={src}
        startFrom={sourceStartFrame}
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
    <AbsoluteFill style={{
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
    }}>
      <OffthreadVideo
        src={src}
        startFrom={sourceStartFrame}
        style={{
          width: "100%",
          height: "100%",
          objectFit: "contain",
        }}
      />
    </AbsoluteFill>
  </AbsoluteFill>
);

// SmartCropLayer: crops tightly around the focus region.
// focus.(x,y) are CENTER coordinates in normalized [0..1]; focus.(w,h) are size.
// We mirror the same object-position math used in the single-mode smart-crop branch.
const SmartCropLayer: React.FC<{
  src: string;
  sourceStartFrame: number;
  videoWidth: number;
  videoHeight: number;
  focus: { x: number; y: number; w: number; h: number };
}> = ({ src, sourceStartFrame, videoWidth, videoHeight, focus }) => {
  // Convert normalized focus to pixel crop rect (same logic as computeCropRect)
  const focusCenterX = focus.x * videoWidth;
  const focusCenterY = focus.y * videoHeight;
  const targetAspect = 9 / 16;
  const sourceAspect = videoWidth / videoHeight;
  let cropW: number;
  let cropH: number;
  if (sourceAspect > targetAspect) {
    cropH = videoHeight;
    cropW = videoHeight * targetAspect;
  } else {
    cropW = videoWidth;
    cropH = videoWidth / targetAspect;
  }
  let cropX = focusCenterX - cropW / 2;
  let cropY = focusCenterY - cropH / 2;
  cropX = Math.max(0, Math.min(videoWidth - cropW, cropX));
  cropY = Math.max(0, Math.min(videoHeight - cropH, cropY));

  const overPanX = videoWidth - cropW;
  const overPanY = videoHeight - cropH;
  const objectPosX = overPanX > 0 ? ((cropX) / overPanX) * 100 : 50;
  const objectPosY = overPanY > 0 ? ((cropY) / overPanY) * 100 : 50;
  const clampedX = Math.max(0, Math.min(100, objectPosX));
  const clampedY = Math.max(0, Math.min(100, objectPosY));

  return (
    <AbsoluteFill style={{ backgroundColor: "black", overflow: "hidden" }}>
      <OffthreadVideo
        src={src}
        startFrom={sourceStartFrame}
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

export const ClipComposition: React.FC<ClipProps> = (props) => {
  const { fps } = useVideoConfig();
  const {
    src, startSec,
    videoWidth, videoHeight,
    cropX, cropY, cropW, cropH,
    displayMode,
    displaySegments,
  } = props;

  // If we have non-empty displaySegments, render a Sequence per segment.
  if (displaySegments && displaySegments.length > 0) {
    return (
      <AbsoluteFill style={{ backgroundColor: "black" }}>
        {displaySegments.map((seg, i) => {
          const fromFrame = Math.floor(seg.startSec * fps);
          const durationFrames = Math.ceil((seg.endSec - seg.startSec) * fps);
          // Source-time = clip.startSec + segment.startSec
          const sourceStartFrame = Math.floor((startSec + seg.startSec) * fps);
          return (
            <Sequence key={i} from={fromFrame} durationInFrames={durationFrames}>
              {seg.mode === "fit" ? (
                <FitLayer src={src} sourceStartFrame={sourceStartFrame} />
              ) : (
                <SmartCropLayer
                  src={src}
                  sourceStartFrame={sourceStartFrame}
                  videoWidth={videoWidth}
                  videoHeight={videoHeight}
                  focus={seg.focus ?? { x: 0.5, y: 0.5, w: 1, h: 1 }}
                />
              )}
            </Sequence>
          );
        })}
      </AbsoluteFill>
    );
  }

  // Fallback: single-mode render using props.displayMode / cropX/Y/W/H.
  // Preserves existing behavior for clips without segments.
  const startFrame = Math.floor(startSec * fps);

  if (displayMode === "fit") {
    // For text-heavy slides: full 16:9 frame visible in 9:16, with a
    // blurred + scaled copy as background. Each layer is wrapped in its
    // own AbsoluteFill so they actually OVERLAP — without that wrap the
    // two <OffthreadVideo> elements just stack vertically (block-flow)
    // and end up split top/bottom instead of overlapping. Also the
    // foreground is explicitly centered via flex so the contained 16:9
    // band sits in the middle of the 9:16 canvas.
    return (
      <AbsoluteFill style={{ backgroundColor: "black", overflow: "hidden" }}>
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
