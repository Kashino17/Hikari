export interface FocusRegion {
  x: number; y: number; w: number; h: number;  // all 0..1 normalized
}
export interface CropInput {
  videoWidth: number;
  videoHeight: number;
  focus: FocusRegion;
  targetAspect: number;  // e.g. 9/16 for portrait phone
}
export interface CropRect {
  x: number; y: number; w: number; h: number;  // pixels in source frame
}

/**
 * Compute a crop rectangle in source-pixel coords that:
 *  - has the target aspect ratio
 *  - is centered on focus-region center if possible
 *  - is clamped to source frame bounds
 *  - has the maximum possible size given the constraints
 *
 * If source aspect already matches target (within 1%), returns full frame.
 *
 * Preconditions: videoWidth > 0, videoHeight > 0, targetAspect > 0. Focus
 * values outside [0,1] are silently clamped via the final bounds check.
 */
export function computeCropRect(input: CropInput): CropRect {
  const { videoWidth: W, videoHeight: H, focus, targetAspect } = input;
  const sourceAspect = W / H;

  // Already the right aspect: no crop needed.
  if (Math.abs(sourceAspect - targetAspect) / targetAspect < 0.01) {
    return { x: 0, y: 0, w: W, h: H };
  }

  // Decide crop dimensions: keep one full axis, compute the other from target aspect.
  let cropW: number;
  let cropH: number;
  if (sourceAspect > targetAspect) {
    // Source is wider than target → keep full height, narrow width
    cropH = H;
    cropW = H * targetAspect;
  } else {
    // Source is taller than target → keep full width, shorter height
    cropW = W;
    cropH = W / targetAspect;
  }

  // Center on focus-region center (in pixel coords)
  const focusCenterX = (focus.x + focus.w / 2) * W;
  const focusCenterY = (focus.y + focus.h / 2) * H;
  let x = focusCenterX - cropW / 2;
  let y = focusCenterY - cropH / 2;

  // Clamp to source frame
  x = Math.max(0, Math.min(W - cropW, x));
  y = Math.max(0, Math.min(H - cropH, y));

  return { x, y, w: cropW, h: cropH };
}
