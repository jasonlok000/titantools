import { Canvas, Rectangle } from "ink";

/** The bar's size and how its segments look: the picked one is taller, and a marker can stand over another. */
const WIDTH = 576;
const HEIGHT = 56;
const MARKER = 6;
const LOW = "#5a5a5a";
const HIGH = "#d8d2c4";
const PICKED = "#f08a24";
const MARK = "#4fb3ff";

const mix = (t: number) => {
  const channel = (i: number) => Math.round(parseInt(LOW.slice(i, i + 2), 16) * (1 - t) + parseInt(HIGH.slice(i, i + 2), 16) * t);
  return `#${[1, 3, 5].map(i => channel(i).toString(16).padStart(2, "0")).join("")}`;
};

/**
 * Ink has no slider: a bar of [count] segments, tapped or dragged along (the pattern of TitanKeys' accent hue bar). Segment
 * [value] is picked; [marker], when given, is a second index shown as a thin line above the bar (the room right now).
 */
export function SegmentSlider({ count, value, marker, onPick }:
  { count: number; value: number; marker?: number | null; onPick: (index: number) => void }) {
  const width = WIDTH / count;
  const top = MARKER + 6;
  return <Canvas width={WIDTH} height={HEIGHT + top}>
    {Array.from({ length: count }, (_, index) => {
      const picked = index === value;
      return <Rectangle key={index} x={index * width + 1} y={picked ? top : top + 10} width={width - 2}
        height={picked ? HEIGHT : HEIGHT - 20} fill={picked ? PICKED : mix(index / Math.max(1, count - 1))}
        onPress={() => onPick(index)} onDragEnter={() => onPick(index)} />;
    })}
    {marker != null && <Rectangle x={Math.min(WIDTH - MARKER, Math.max(0, (marker + 0.5) * width - MARKER / 2))} y={0}
      width={MARKER} height={HEIGHT + top} fill={MARK} />}
  </Canvas>;
}
