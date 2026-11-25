import { Canvas, CanvasPath, Rectangle } from "ink";

/** A minimal bar in TitanKeys' style: a thin track, the filled part in the accent, a round knob. */
const WIDTH = 576;
const HEIGHT = 64;
const TRACK = 6;
const KNOB = 30;
const EMPTY = "#3a3a3a";
const FILLED = "#f08a24";
const KNOB_FILL = "#f4efe6";
const MARK = "#4fb3ff";

/**
 * Ink has no slider: the bar is cut into [steps] invisible strips, one per value from [min] to [max], so a finger moving
 * along it lands on each value in turn (smooth to the eye). A tap picks the value under it; dragging picks each value it
 * passes. A swipe that goes up or down over the bar stays in one strip, so it never changes the value. [marker], when given,
 * is a value shown as a thin blue line (the room right now).
 */
export function Slider({ min, max, value, marker, onPick, onDragStart, onDragEnd }: {
  min: number; max: number; value: number; marker?: number | null;
  onPick: (value: number) => void; onDragStart?: () => void; onDragEnd?: () => void;
}) {
  const steps = max - min + 1;
  const strip = WIDTH / steps;
  const at = (v: number) => KNOB / 2 + (Math.min(max, Math.max(min, v)) - min) / Math.max(1, max - min) * (WIDTH - KNOB);
  const mid = HEIGHT / 2;
  const x = at(value);
  return <Canvas width={WIDTH} height={HEIGHT}>
    <Rectangle x={KNOB / 2} y={mid - TRACK / 2} width={WIDTH - KNOB} height={TRACK} fill={EMPTY} />
    <Rectangle x={KNOB / 2} y={mid - TRACK / 2} width={Math.max(0, x - KNOB / 2)} height={TRACK} fill={FILLED} />
    {marker != null && <Rectangle x={at(marker) - 2} y={mid - 22} width={4} height={44} fill={MARK} />}
    {Array.from({ length: steps }, (_, index) => <Rectangle key={index} x={index * strip} y={0} width={strip} height={HEIGHT}
      onPress={() => onPick(min + index)} onDragEnter={() => onPick(min + index)}
      onDragStart={onDragStart} onDragEnd={onDragEnd} />)}
    <CanvasPath points={[[x, mid], [x + 0.01, mid]]} stroke={KNOB_FILL} strokeWidth={KNOB} />
  </Canvas>;
}
