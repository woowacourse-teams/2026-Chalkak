// 가상 카메라 계산. 풍경 좌표는 원본 픽셀, 결과 프레임은 풍경 대비 비율(0~1)로 다룬다.

export type Size = { width: number; height: number };
export type Point = { x: number; y: number };
export type Rect = { x: number; y: number; width: number; height: number };

/** 풍경의 왼쪽 위가 무대에서 놓인 위치(x, y)와 배율(scale: 무대 px / 풍경 px). */
export type View = { x: number; y: number; scale: number };

/** 다른 작가의 프레임. 중심(cx, cy)과 높이(h)는 풍경 대비 비율이다. */
export type SceneFrame = { cx: number; cy: number; h: number };

export const VIEWFINDER_ASPECT = 9 / 16;
export const MAX_ZOOM = 4;

const VIEWFINDER_HEIGHT_RATIO = 0.9;
const VIEWFINDER_MAX_WIDTH_RATIO = 0.86;

export function viewfinderIn(stage: Size): Rect {
  let height = stage.height * VIEWFINDER_HEIGHT_RATIO;
  let width = height * VIEWFINDER_ASPECT;
  if (width > stage.width * VIEWFINDER_MAX_WIDTH_RATIO) {
    width = stage.width * VIEWFINDER_MAX_WIDTH_RATIO;
    height = width / VIEWFINDER_ASPECT;
  }
  return { x: (stage.width - width) / 2, y: (stage.height - height) / 2, width, height };
}

/** 뷰파인더가 풍경 밖을 비추지 않는 최소 배율. 이때를 1.0x로 표시한다. */
export function minScale(scene: Size, viewfinder: Rect): number {
  return Math.max(viewfinder.height / scene.height, viewfinder.width / scene.width);
}

export function zoomOf(view: View, scene: Size, viewfinder: Rect): number {
  return view.scale / minScale(scene, viewfinder);
}

export function clampView(view: View, scene: Size, viewfinder: Rect): View {
  const min = minScale(scene, viewfinder);
  const scale = Math.min(min * MAX_ZOOM, Math.max(min, view.scale));
  const x = Math.min(viewfinder.x, Math.max(viewfinder.x + viewfinder.width - scene.width * scale, view.x));
  const y = Math.min(viewfinder.y, Math.max(viewfinder.y + viewfinder.height - scene.height * scale, view.y));
  return { x, y, scale };
}

/** 무대의 한 점(anchor)을 고정한 채 배율을 바꾼다. */
export function zoomAt(view: View, nextScale: number, anchor: Point, scene: Size, viewfinder: Rect): View {
  const min = minScale(scene, viewfinder);
  const scale = Math.min(min * MAX_ZOOM, Math.max(min, nextScale));
  const sceneX = (anchor.x - view.x) / view.scale;
  const sceneY = (anchor.y - view.y) / view.scale;
  return clampView({ x: anchor.x - sceneX * scale, y: anchor.y - sceneY * scale, scale }, scene, viewfinder);
}

/** 풍경의 한 점(비율)이 뷰파인더 가운데 오도록 놓는다. */
export function centerOn(target: Point, zoom: number, scene: Size, viewfinder: Rect): View {
  const scale = minScale(scene, viewfinder) * zoom;
  return clampView(
    {
      x: viewfinder.x + viewfinder.width / 2 - target.x * scene.width * scale,
      y: viewfinder.y + viewfinder.height / 2 - target.y * scene.height * scale,
      scale,
    },
    scene,
    viewfinder,
  );
}

/** 지금 뷰파인더가 비추는 영역을 풍경 대비 비율로 돌려준다. */
export function captureRect(view: View, scene: Size, viewfinder: Rect): Rect {
  return {
    x: (viewfinder.x - view.x) / view.scale / scene.width,
    y: (viewfinder.y - view.y) / view.scale / scene.height,
    width: viewfinder.width / view.scale / scene.width,
    height: viewfinder.height / view.scale / scene.height,
  };
}

/** 프레임 정의를 풍경 안에 들어오는 9:16 비율 사각형으로 바꾼다. */
export function frameRect(frame: SceneFrame, sceneAspect: number): Rect {
  const height = frame.h;
  const width = (height * VIEWFINDER_ASPECT) / sceneAspect;
  const x = Math.max(0, Math.min(1 - width, frame.cx - width / 2));
  const y = Math.max(0, Math.min(1 - height, frame.cy - height / 2));
  return { x, y, width, height };
}

/** 두 프레임이 겹치는 정도(IoU). 0이면 겹치지 않고 1이면 같다. */
export function overlapRatio(a: Rect, b: Rect): number {
  const ix = Math.max(0, Math.min(a.x + a.width, b.x + b.width) - Math.max(a.x, b.x));
  const iy = Math.max(0, Math.min(a.y + a.height, b.y + b.height) - Math.max(a.y, b.y));
  const intersection = ix * iy;
  const union = a.width * a.height + b.width * b.height - intersection;
  return union > 0 ? intersection / union : 0;
}

/** 풍경 한 장을 배경으로 깔고 비율 사각형만큼 잘라 보이게 하는 CSS 값. */
export function cropBackground(rect: Rect): { backgroundSize: string; backgroundPosition: string } {
  const position = (offset: number, size: number) => (size < 1 - 1e-6 ? (offset / (1 - size)) * 100 : 0);
  return {
    backgroundSize: `${100 / rect.width}% auto`,
    backgroundPosition: `${position(rect.x, rect.width)}% ${position(rect.y, rect.height)}%`,
  };
}
