export type SignaturePoint = { x: number; y: number };
export type SignatureStroke = SignaturePoint[];

export type PathCommand =
  | { type: "move"; point: SignaturePoint }
  | { type: "line"; point: SignaturePoint }
  | { type: "quad"; control: SignaturePoint; point: SignaturePoint };

export const PREVIEW_STROKES: SignatureStroke[] = [[
  { x: 0.12, y: 0.64 },
  { x: 0.24, y: 0.52 },
  { x: 0.38, y: 0.68 },
  { x: 0.55, y: 0.48 },
  { x: 0.78, y: 0.56 },
]];

export function clampRatio(value: number) {
  return Math.min(1, Math.max(0, value));
}

export function normalizePoint(point: SignaturePoint, rect: Pick<DOMRect, "left" | "top" | "width" | "height">): SignaturePoint {
  return { x: clampRatio((point.x - rect.left) / rect.width), y: clampRatio((point.y - rect.top) / rect.height) };
}

export function smoothPath(points: SignaturePoint[]): PathCommand[] {
  if (!points.length) return [];
  if (points.length === 1) return [{ type: "move", point: points[0] }];
  const commands: PathCommand[] = [{ type: "move", point: points[0] }];
  if (points.length === 2) return [...commands, { type: "line", point: points[1] }];
  for (let index = 1; index < points.length - 1; index += 1) {
    const current = points[index];
    const next = points[index + 1];
    commands.push({ type: "quad", control: current, point: { x: (current.x + next.x) / 2, y: (current.y + next.y) / 2 } });
  }
  commands.push({ type: "line", point: points[points.length - 1] });
  return commands;
}

export function signatureLayout(strokes: SignatureStroke[], width = 1024, height = 512, padding = 48) {
  const points = strokes.flat();
  if (!points.length) return null;
  const minX = Math.min(...points.map((point) => point.x));
  const maxX = Math.max(...points.map((point) => point.x));
  const minY = Math.min(...points.map((point) => point.y));
  const maxY = Math.max(...points.map((point) => point.y));
  const contentWidth = Math.max(maxX - minX, 0.02);
  const contentHeight = Math.max(maxY - minY, 0.02);
  const scale = Math.min((width - padding * 2) / contentWidth, (height - padding * 2) / contentHeight);
  return { minX, minY, scale, offsetX: (width - contentWidth * scale) / 2, offsetY: (height - contentHeight * scale) / 2 };
}

export function mapSignaturePoint(point: SignaturePoint, layout: NonNullable<ReturnType<typeof signatureLayout>>) {
  return { x: layout.offsetX + (point.x - layout.minX) * layout.scale, y: layout.offsetY + (point.y - layout.minY) * layout.scale };
}

export function mapNormalizedPoint(point: SignaturePoint, width: number, height: number) {
  return { x: point.x * width, y: point.y * height };
}

export function signatureDataUrl(strokes: SignatureStroke[]) {
  if (typeof document === "undefined" || !strokes.length) return "";
  const canvas = document.createElement("canvas");
  canvas.width = 1024;
  canvas.height = 512;
  const context = canvas.getContext("2d");
  if (!context || !drawFittedSignature(context, strokes, 1024, 512, 14, 48)) return "";
  return canvas.toDataURL("image/png");
}

export function drawFittedSignature(context: CanvasRenderingContext2D, strokes: SignatureStroke[], width: number, height: number, strokeWidth: number, padding: number) {
  const layout = signatureLayout(strokes, width, height, padding);
  if (!layout) return false;
  context.strokeStyle = "white";
  context.fillStyle = "white";
  context.lineWidth = strokeWidth;
  context.lineCap = "round";
  context.lineJoin = "round";
  for (const stroke of strokes) {
    const points = stroke.map((point) => mapSignaturePoint(point, layout));
    const first = points[0];
    if (!first) continue;
    if (points.length === 1) {
      context.beginPath();
      context.arc(first.x, first.y, strokeWidth / 2, 0, Math.PI * 2);
      context.fill();
      continue;
    }
    context.beginPath();
    for (const command of smoothPath(points)) {
      if (command.type === "move") context.moveTo(command.point.x, command.point.y);
      else if (command.type === "line") context.lineTo(command.point.x, command.point.y);
      else context.quadraticCurveTo(command.control.x, command.control.y, command.point.x, command.point.y);
    }
    context.stroke();
  }
  return true;
}

/** Draw normalized input points without changing their relative position. */
export function drawNormalizedSignature(context: CanvasRenderingContext2D, strokes: SignatureStroke[], width: number, height: number, strokeWidth: number) {
  context.strokeStyle = "white";
  context.fillStyle = "white";
  context.lineWidth = strokeWidth;
  context.lineCap = "round";
  context.lineJoin = "round";
  for (const stroke of strokes) {
    const points = stroke.map((point) => mapNormalizedPoint(point, width, height));
    const first = points[0];
    if (!first) continue;
    if (points.length === 1) {
      context.beginPath();
      context.arc(first.x, first.y, strokeWidth / 2, 0, Math.PI * 2);
      context.fill();
      continue;
    }
    context.beginPath();
    for (const command of smoothPath(points)) {
      if (command.type === "move") context.moveTo(command.point.x, command.point.y);
      else if (command.type === "line") context.lineTo(command.point.x, command.point.y);
      else context.quadraticCurveTo(command.control.x, command.control.y, command.point.x, command.point.y);
    }
    context.stroke();
  }
  return true;
}
