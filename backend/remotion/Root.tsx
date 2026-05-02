import React from "react";
import { Composition } from "remotion";
import { ClipComposition, clipPropsSchema } from "./ClipComposition";

export const RemotionRoot: React.FC = () => {
  return (
    <Composition
      id="Clip"
      component={ClipComposition}
      schema={clipPropsSchema}
      width={1080}
      height={1920}
      fps={30}
      durationInFrames={300}
      defaultProps={{
        src: "",
        startSec: 0,
        endSec: 60,
        videoWidth: 1920,
        videoHeight: 1080,
        cropX: 0,
        cropY: 0,
        cropW: 1920,
        cropH: 1080,
      }}
      calculateMetadata={({ props }) => ({
        durationInFrames: Math.ceil((props.endSec - props.startSec) * 30),
      })}
    />
  );
};
