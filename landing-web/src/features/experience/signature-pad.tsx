"use client";

import { useEffect, useRef } from "react";

import { drawNormalizedSignature, normalizePoint, PREVIEW_STROKES, type SignatureStroke } from "./signature";
import styles from "./experience.module.css";

type Props = { strokes: SignatureStroke[]; onChange: (strokes: SignatureStroke[]) => void };

export function SignaturePad({ strokes, onChange }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const drawing = useRef<SignatureStroke | null>(null);
  const activePointer = useRef<number | null>(null);
  const strokesRef = useRef(strokes);
  useEffect(() => { strokesRef.current = strokes; }, [strokes]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const redraw = () => {
      const rect = canvas.getBoundingClientRect();
      const ratio = window.devicePixelRatio || 1;
      canvas.width = Math.max(1, Math.round(rect.width * ratio));
      canvas.height = Math.max(1, Math.round(rect.height * ratio));
      const context = canvas.getContext("2d");
      if (!context) return;
      context.setTransform(1, 0, 0, 1, 0, 0);
      context.clearRect(0, 0, canvas.width, canvas.height);
      drawNormalizedSignature(context, strokesRef.current, canvas.width, canvas.height, 4 * ratio);
    };
    redraw();
    const observer = new ResizeObserver(redraw);
    observer.observe(canvas);
    window.addEventListener("resize", redraw);
    return () => { observer.disconnect(); window.removeEventListener("resize", redraw); };
  }, [strokes]);

  const pointFromEvent = (event: React.PointerEvent<HTMLCanvasElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    return normalizePoint({ x: event.clientX, y: event.clientY }, rect);
  };
  const updateStrokes = (next: SignatureStroke[]) => {
    strokesRef.current = next;
    onChange(next);
  };
  const endPointer = (event: React.PointerEvent<HTMLCanvasElement>) => {
    if (activePointer.current !== event.pointerId) return;
    drawing.current = null;
    activePointer.current = null;
  };

  return (
    <>
    <div className={styles.signatureInput}>
      <div className={styles.signatureCanvasWrap}>
        <canvas
          ref={canvasRef}
          className={styles.signatureCanvas}
          aria-label="사인 입력창"
          role="img"
          onPointerDown={(event) => {
            if (event.button !== 0 || activePointer.current !== null) return;
            event.currentTarget.setPointerCapture(event.pointerId);
            activePointer.current = event.pointerId;
            drawing.current = [pointFromEvent(event)];
            updateStrokes([...strokesRef.current, drawing.current]);
          }}
          onPointerMove={(event) => {
            if (!drawing.current || activePointer.current !== event.pointerId) return;
            drawing.current = [...drawing.current, pointFromEvent(event)];
            updateStrokes([...strokesRef.current.slice(0, -1), drawing.current]);
          }}
          onPointerUp={(event) => { endPointer(event); if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId); }}
          onPointerCancel={endPointer}
          onLostPointerCapture={endPointer}
        />
        {!strokes.length && <span className={styles.signaturePlaceholder} aria-hidden="true">사인</span>}
      </div>
      <p className={styles.signatureCaption}>여기에 손가락으로 그리기</p>
    </div>
    <div className={styles.signatureControls}>
      <button type="button" className={styles.signatureControl} aria-label="마지막 획 되돌리기" disabled={!strokes.length} onClick={() => updateStrokes(strokesRef.current.slice(0, -1))}><span aria-hidden="true">↶</span> 되돌리기</button>
      <button type="button" className={styles.signatureControl} disabled={!strokes.length} onClick={() => updateStrokes([])}>전체 지우기</button>
      <button type="button" className={styles.signatureExample} onClick={() => updateStrokes(PREVIEW_STROKES)}>예시 사인으로 채우기</button>
    </div>
    </>
  );
}

