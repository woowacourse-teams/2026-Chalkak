"use client";

import Image from "next/image";
import { useEffect, useId, useRef, useState, type KeyboardEvent } from "react";
import { createPortal } from "react-dom";

import styles from "./post-image-viewer.module.css";

interface PostImageViewerProps {
  src?: string | null;
  alt: string;
}

export function PostImageViewer({ src, alt }: PostImageViewerProps) {
  return <ImageViewerContent alt={alt} key={src ?? "missing"} src={src} />;
}

function ImageFallback() {
  return <span aria-label="이미지를 불러올 수 없음" className={styles.fallback} role="img">이미지 없음</span>;
}

function ImageViewerContent({ src, alt }: PostImageViewerProps) {
  const [failed, setFailed] = useState(false);
  const [open, setOpen] = useState(false);

  if (!src || failed) {
    return <div className={styles.emptyFrame}><ImageFallback /></div>;
  }

  return (
    <>
      <button aria-label="사진 확대" className={styles.preview} onClick={() => setOpen(true)} type="button">
        <span className={styles.imageArea}>
          <Image alt={alt} className={styles.image} fill loading="eager" onError={() => setFailed(true)} sizes="(max-width: 1024px) 100vw, 55vw" src={src} unoptimized />
        </span>
        <span className={styles.expandLabel}>사진 확대 <span aria-hidden="true">↗</span></span>
      </button>
      {open ? createPortal(<ExpandedImage alt={alt} onClose={() => setOpen(false)} src={src} />, document.body) : null}
    </>
  );
}

function ExpandedImage({ src, alt, onClose }: { src: string; alt: string; onClose: () => void }) {
  const [zoomed, setZoomed] = useState(false);
  const [failed, setFailed] = useState(false);
  const dialogRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  const viewportRef = useRef<HTMLDivElement>(null);
  const id = useId();

  useEffect(() => {
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const previousOverflow = document.body.style.overflow;
    const keepFocusInside = (event: FocusEvent) => {
      if (event.target instanceof Node && !dialogRef.current?.contains(event.target)) {
        closeRef.current?.focus();
      }
    };
    document.body.style.overflow = "hidden";
    document.addEventListener("focusin", keepFocusInside);
    closeRef.current?.focus();
    return () => {
      document.removeEventListener("focusin", keepFocusInside);
      document.body.style.overflow = previousOverflow;
      if (previousFocus?.isConnected) previousFocus.focus({ preventScroll: true });
    };
  }, []);

  useEffect(() => {
    const viewport = viewportRef.current;
    if (!viewport) return;
    viewport.scrollLeft = zoomed ? (viewport.scrollWidth - viewport.clientWidth) / 2 : 0;
    viewport.scrollTop = zoomed ? (viewport.scrollHeight - viewport.clientHeight) / 2 : 0;
  }, [zoomed]);

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape") {
      event.preventDefault();
      event.stopPropagation();
      onClose();
      return;
    }
    if (event.key !== "Tab") return;
    const controls = Array.from(dialogRef.current?.querySelectorAll<HTMLElement>('button:not([disabled]), [tabindex="0"]') ?? []);
    const first = controls[0];
    const last = controls.at(-1);
    if (!first || !last) return;
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  return (
    <div aria-describedby={id + "-help"} aria-labelledby={id + "-title"} aria-modal="true" className={styles.modal} onKeyDown={handleKeyDown} ref={dialogRef} role="dialog">
      <h2 className={styles.srOnly} id={id + "-title"}>사진 확대</h2>
      <div className={styles.toolbar}>
        <button aria-pressed={!zoomed} disabled={failed} onClick={() => setZoomed(false)} type="button">화면 맞춤</button>
        <button aria-pressed={zoomed} disabled={failed} onClick={() => setZoomed(true)} type="button">2배 확대</button>
        <button className={styles.closeButton} onClick={onClose} ref={closeRef} type="button">닫기</button>
      </div>
      <div aria-label="확대 사진" className={styles.viewport} data-zoom={zoomed ? "2x" : "fit"} ref={viewportRef} role="region" tabIndex={0}>
        {failed ? <ImageFallback /> : (
          <div className={zoomed ? styles.zoomedCanvas : styles.canvas}>
            <Image alt={alt} className={styles.image} fill loading="eager" onError={() => setFailed(true)} sizes={zoomed ? "200vw" : "100vw"} src={src} unoptimized />
          </div>
        )}
      </div>
      <p className={styles.help} id={id + "-help"}>{failed ? "사진을 불러오지 못했습니다. 닫고 다시 시도해 주세요." : "확대 후 사진을 밀거나 스크롤해 이동할 수 있습니다."}</p>
    </div>
  );
}
