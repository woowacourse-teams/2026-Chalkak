"use client";

import { useCallback, useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type KeyboardEvent, type PointerEvent } from "react";

import { useClientValue } from "@/shared/use-client-value";

import {
  captureRect,
  centerOn,
  clampView,
  cropBackground,
  MAX_ZOOM,
  viewfinderIn,
  zoomAt,
  zoomOf,
  type Point,
  type Rect,
  type Size,
  type View,
} from "./camera";
import { Reveal } from "./reveal";
import { checkTitle, SHARING_ENABLED, TITLE_MAX_LENGTH } from "./shared-shot";
import { SignatureSheet } from "./signature-sheet";
import { signatureDataUrl } from "./signature";
import type { SignatureStroke } from "./signature";
import { onggiScene } from "./scene";
import { playShutterSound } from "./shutter-sound";
import styles from "./experience.module.css";

type Phase = "topic" | "shoot" | "review" | "sign" | "signPreview" | "reveal";

const scene = onggiScene;
const PAN_STEP = 48;
const KEY_ZOOM_FACTOR = 1.2;
const BUTTON_ZOOM_FACTOR = 1.25;
const WHEEL_ZOOM_SPEED = 0.0015;
const FLASH_TO_REVIEW_MS = 260;

function todayLabel() {
  return new Intl.DateTimeFormat("ko-KR", { month: "long", day: "numeric", timeZone: "Asia/Seoul" }).format(new Date());
}

/** 저사양 기기와 휴대폰에는 절반 해상도의 풍경을 준다. */
function pickSceneSource() {
  const compact = window.matchMedia("(max-width: 768px), (pointer: coarse)").matches;
  return compact ? scene.sources.compact : scene.sources.full;
}

/** showSoundToggle=false면 셔터 소리 버튼을 숨기고 소리 없이 찍는다. */
export function Experience({ showSoundToggle = true }: { showSoundToggle?: boolean }) {
  const stageRef = useRef<HTMLDivElement>(null);
  const revealRef = useRef<HTMLDivElement>(null);
  const pointers = useRef(new Map<number, Point>());
  const pinch = useRef<{ distance: number; mid: Point } | null>(null);
  const stageSize = useRef<Size | null>(null);

  const [phase, setPhase] = useState<Phase>("topic");
  const [stage, setStage] = useState<Size | null>(null);
  const [view, setView] = useState<View | null>(null);
  const [source, setSource] = useState<string>(scene.sources.preview);
  const [shot, setShot] = useState<Rect | null>(null);
  const [flashKey, setFlashKey] = useState(0);
  const [sound, setSound] = useState(false);
  const [touched, setTouched] = useState(false);
  const [strokes, setStrokes] = useState<SignatureStroke[]>([]);
  const [signaturePng, setSignaturePng] = useState("");
  const [title, setTitle] = useState<string | null>(null);
  const date = useClientValue(todayLabel, "");

  const viewfinder = stage ? viewfinderIn(stage) : null;

  // 무대 크기를 따라가며, 크기가 바뀌어도 보던 곳과 배율을 유지한다.
  useLayoutEffect(() => {
    const element = stageRef.current;
    if (!element) return;
    const observer = new ResizeObserver(([entry]) => {
      const next = { width: entry.contentRect.width, height: entry.contentRect.height };
      const previous = stageSize.current;
      stageSize.current = next;
      setStage(next);
      setView((current) => {
        const nextFinder = viewfinderIn(next);
        if (!current || !previous) return centerOn(scene.start, 1, scene.size, nextFinder);
        const oldFinder = viewfinderIn(previous);
        const center = {
          x: (oldFinder.x + oldFinder.width / 2 - current.x) / current.scale / scene.size.width,
          y: (oldFinder.y + oldFinder.height / 2 - current.y) / current.scale / scene.size.height,
        };
        return centerOn(center, zoomOf(current, scene.size, oldFinder), scene.size, nextFinder);
      });
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  // 미리보기 이미지로 먼저 그리고, 큰 이미지를 받으면 바꾼다.
  useEffect(() => {
    const target = pickSceneSource();
    const image = new Image();
    image.onload = () => setSource(target);
    image.src = target;
  }, []);

  const update = useCallback(
    (next: (current: View) => View) => {
      if (!viewfinder) return;
      setView((current) => (current ? clampView(next(current), scene.size, viewfinder) : current));
    },
    [viewfinder],
  );

  const zoomAround = useCallback(
    (factor: number, anchor?: Point) => {
      if (!viewfinder) return;
      const point = anchor ?? { x: viewfinder.x + viewfinder.width / 2, y: viewfinder.y + viewfinder.height / 2 };
      setView((current) => (current ? zoomAt(current, current.scale * factor, point, scene.size, viewfinder) : current));
    },
    [viewfinder],
  );

  // 페이지 스크롤과 겹치지 않도록 휠은 passive가 아닌 리스너로 받는다.
  useEffect(() => {
    const element = stageRef.current;
    if (!element || phase !== "shoot") return;
    const onWheel = (event: WheelEvent) => {
      event.preventDefault();
      const box = element.getBoundingClientRect();
      zoomAround(Math.exp(-event.deltaY * WHEEL_ZOOM_SPEED), { x: event.clientX - box.left, y: event.clientY - box.top });
      setTouched(true);
    };
    element.addEventListener("wheel", onWheel, { passive: false });
    return () => element.removeEventListener("wheel", onWheel);
  }, [phase, zoomAround]);

  const pinchState = () => {
    const [a, b] = [...pointers.current.values()];
    return { distance: Math.hypot(a.x - b.x, a.y - b.y) || 1, mid: { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 } };
  };

  const onPointerDown = (event: PointerEvent<HTMLDivElement>) => {
    if (phase !== "shoot") return;
    event.currentTarget.setPointerCapture(event.pointerId);
    pointers.current.set(event.pointerId, { x: event.clientX, y: event.clientY });
    if (pointers.current.size === 2) pinch.current = pinchState();
    setTouched(true);
  };

  const onPointerMove = (event: PointerEvent<HTMLDivElement>) => {
    const last = pointers.current.get(event.pointerId);
    if (!last) return;
    const dx = event.clientX - last.x;
    const dy = event.clientY - last.y;
    pointers.current.set(event.pointerId, { x: event.clientX, y: event.clientY });

    if (pointers.current.size === 1) {
      update((current) => ({ ...current, x: current.x + dx, y: current.y + dy }));
      return;
    }
    const next = pinchState();
    const previous = pinch.current;
    pinch.current = next;
    if (!previous || !viewfinder) return;
    const box = event.currentTarget.getBoundingClientRect();
    const anchor = { x: next.mid.x - box.left, y: next.mid.y - box.top };
    setView((current) => {
      if (!current) return current;
      const panned = { ...current, x: current.x + next.mid.x - previous.mid.x, y: current.y + next.mid.y - previous.mid.y };
      return zoomAt(panned, panned.scale * (next.distance / previous.distance), anchor, scene.size, viewfinder);
    });
  };

  const onPointerEnd = (event: PointerEvent<HTMLDivElement>) => {
    pointers.current.delete(event.pointerId);
    pinch.current = pointers.current.size === 2 ? pinchState() : null;
  };

  const shoot = () => {
    if (phase !== "shoot" || !view || !viewfinder) return;
    if (sound) playShutterSound();
    setFlashKey((key) => key + 1);
    setShot(captureRect(view, scene.size, viewfinder));
    window.setTimeout(() => setPhase("review"), FLASH_TO_REVIEW_MS);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (phase !== "shoot") return;
    const pans: Record<string, [number, number]> = {
      ArrowLeft: [PAN_STEP, 0],
      ArrowRight: [-PAN_STEP, 0],
      ArrowUp: [0, PAN_STEP],
      ArrowDown: [0, -PAN_STEP],
    };
    if (event.key in pans) {
      const [dx, dy] = pans[event.key];
      update((current) => ({ ...current, x: current.x + dx, y: current.y + dy }));
    } else if (event.key === "+" || event.key === "=") zoomAround(KEY_ZOOM_FACTOR);
    else if (event.key === "-") zoomAround(1 / KEY_ZOOM_FACTOR);
    else if (event.key === " " || event.key === "Enter") shoot();
    else return;
    event.preventDefault();
    setTouched(true);
  };

  const start = () => {
    setPhase("shoot");
    stageRef.current?.focus({ preventScroll: true });
  };

  const confirm = (nextTitle: string | null) => {
    setTitle(nextTitle);
    setPhase("sign");
  };

  const confirmSignature = () => {
    setSignaturePng(signatureDataUrl(strokes));
    setPhase("signPreview");
  };

  const confirmPreview = () => {
    setPhase("reveal");
    requestAnimationFrame(() => revealRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }));
  };

  const retake = () => {
    setPhase("shoot");
    stageRef.current?.focus({ preventScroll: true });
  };

  const again = () => {
    setPhase("shoot");
    stageRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
    stageRef.current?.focus({ preventScroll: true });
  };

  const zoom = view && viewfinder ? zoomOf(view, scene.size, viewfinder) : 1;
  const steps: { id: Phase[]; label: string }[] = [
    { id: ["topic"], label: "주제 받기" },
    { id: ["shoot", "review"], label: "골목에서 찍기" },
    { id: ["sign", "signPreview"], label: "사인 남기기" },
    { id: ["reveal"], label: "다른 시선 보기" },
  ];

  return (
    <div className={styles.experience}>
      <ol className={styles.steps} aria-label="체험 단계">
        {steps.map((step, index) => (
          <li key={step.label} className={step.id.includes(phase) ? styles.stepOn : undefined} aria-current={step.id.includes(phase) ? "step" : undefined}>
            <span aria-hidden="true">{index + 1}</span>
            {step.label}
          </li>
        ))}
      </ol>

      <div
        ref={stageRef}
        className={styles.stage}
        style={{ "--stage-backdrop": `url(${scene.sources.preview})` } as CSSProperties}
        data-phase={phase}
        tabIndex={0}
        role="application"
        aria-roledescription="가상 카메라"
        aria-label={`${scene.alt}. 드래그하거나 방향키로 둘러보고, 휠이나 두 손가락, +와 - 키로 확대해요. 스페이스나 엔터로 찍어요.`}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerEnd}
        onPointerCancel={onPointerEnd}
        onKeyDown={onKeyDown}
      >
        {view && (
          // 확대해도 선명하도록 transform scale 대신 실제 크기를 바꾼다.
          // eslint-disable-next-line @next/next/no-img-element
          <img
            className={styles.scene}
            src={source}
            alt=""
            draggable={false}
            style={{
              width: scene.size.width * view.scale,
              height: scene.size.height * view.scale,
              transform: `translate3d(${view.x}px, ${view.y}px, 0)`,
            }}
          />
        )}
        {viewfinder && (
          <div
            className={styles.viewfinder}
            aria-hidden="true"
            style={{ left: viewfinder.x, top: viewfinder.y, width: viewfinder.width, height: viewfinder.height }}
          >
            <span className={styles.grid} />
            <span className={styles.focus} />
            <span className={styles.topicChip}>오늘의 주제 · {scene.topic}</span>
            <span className={styles.zoomChip}>{zoom.toFixed(1)}x</span>
            {phase === "shoot" && !touched && <span className={styles.coach}>끌어서 둘러보고, 확대해서 찾아보세요</span>}
          </div>
        )}
        {flashKey > 0 && <span key={flashKey} className={styles.flash} aria-hidden="true" />}

        {phase === "topic" && (
          <div className={styles.intro}>
            <div className={styles.topicCard}>
              <p className={styles.topicDate}>{date ? `${date} · 오늘의 주제` : "오늘의 주제"}</p>
              <p className={styles.topicTitle}>{scene.topic}</p>
              <p className={styles.topicText}>
                {scene.intro}
                <br />
                정답은 없어요. 떠오르는 대로 찍어보세요.
              </p>
              <button type="button" className={styles.primary} onClick={start}>
                카메라 들기
              </button>
            </div>
          </div>
        )}
      </div>

      <div className={styles.controls}>
        <button type="button" className={styles.round} onClick={() => zoomAround(1 / BUTTON_ZOOM_FACTOR)} disabled={phase !== "shoot" || zoom <= 1.001} aria-label="축소">
          −
        </button>
        <input
          className={styles.range}
          type="range"
          min={1}
          max={MAX_ZOOM}
          step={0.01}
          value={zoom}
          disabled={phase !== "shoot"}
          aria-label="확대 배율"
          onChange={(event) => zoomAround(Number(event.target.value) / zoom)}
        />
        <button type="button" className={styles.round} onClick={() => zoomAround(BUTTON_ZOOM_FACTOR)} disabled={phase !== "shoot" || zoom >= MAX_ZOOM - 0.001} aria-label="확대">
          +
        </button>
        <button type="button" className={styles.shutter} onClick={shoot} disabled={phase !== "shoot"} aria-label="찍기">
          <span />
        </button>
        {showSoundToggle && (
          <button type="button" className={styles.round} aria-pressed={sound} aria-label="셔터 소리" onClick={() => setSound((on) => !on)}>
            <span aria-hidden="true">{sound ? "🔊" : "🔇"}</span>
          </button>
        )}
      </div>
      <p className={styles.note}>
        {SHARING_ENABLED
          ? "체험용 화면이에요. 찍은 장면의 위치와 제목만 익명으로 다른 방문자와 공유하고, 그린 사인은 어디에도 저장하지 않아요."
          : "체험용 화면이에요. 찍은 사진과 제목, 그린 사인은 어디에도 저장되지 않아요."}
      </p>

      {phase === "review" && shot && <ReviewSheet shot={shot} source={source} initialTitle={title ?? ""} onRetake={retake} onConfirm={confirm} />}
      {(phase === "sign" || phase === "signPreview") && shot && <SignatureSheet mode={phase === "sign" ? "sign" : "preview"} signaturePng={signaturePng} strokes={strokes} shot={shot} source={source} onChange={setStrokes} onBack={phase === "sign" ? retake : () => setPhase("sign")} onConfirm={phase === "sign" ? confirmSignature : confirmPreview} />}

      <div ref={revealRef} className={styles.revealAnchor}>
        {phase === "reveal" && shot && <Reveal shot={shot} title={title} source={source} signature={signaturePng} onAgain={again} />}
      </div>
    </div>
  );
}

function ReviewSheet({
  shot,
  source,
  initialTitle,
  onRetake,
  onConfirm,
}: {
  shot: Rect;
  source: string;
  initialTitle: string;
  onRetake: () => void;
  onConfirm: (title: string | null) => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);
  const [draft, setDraft] = useState(initialTitle);
  const [titleError, setTitleError] = useState("");
  const length = [...draft.trim()].length;

  const submit = () => {
    const result = checkTitle(draft);
    if (!result.ok) {
      setTitleError(result.reason);
      return;
    }
    onConfirm(result.title);
  };

  // showModal()은 첫 번째 버튼에 포커스를 주므로, 연 뒤에 확인 버튼으로 옮긴다.
  useEffect(() => {
    const dialog = dialogRef.current;
    if (dialog && !dialog.open) dialog.showModal();
    confirmRef.current?.focus();
    return () => dialog?.close();
  }, []);

  return (
    <dialog
      ref={dialogRef}
      className={styles.sheet}
      aria-labelledby="review-title"
      onCancel={(event) => {
        event.preventDefault();
        onRetake();
      }}
    >
      <h2 id="review-title" className={styles.sheetTitle}>
        나의 &lsquo;{scene.topic}&rsquo;
      </h2>
      <div className={styles.crop} style={{ backgroundImage: `url(${source})`, ...cropBackground(shot) }} role="img" aria-label="방금 찍은 한 장" />
      <form
        className={styles.titleField}
        onSubmit={(event) => {
          event.preventDefault();
          submit();
        }}
      >
        <label htmlFor="shot-title">
          제목 <span>선택</span>
        </label>
        <div className={styles.titleInputRow}>
          <input
            id="shot-title"
            value={draft}
            placeholder="이 한 장을 부르는 이름"
            autoComplete="off"
            enterKeyHint="done"
            aria-invalid={titleError ? true : undefined}
            aria-describedby="shot-title-help"
            onChange={(event) => {
              setDraft(event.target.value);
              setTitleError("");
            }}
          />
          <span className={styles.titleCount} data-over={length > TITLE_MAX_LENGTH || undefined} aria-hidden="true">
            {length}/{TITLE_MAX_LENGTH}
          </span>
        </div>
        <p id="shot-title-help" className={titleError ? styles.titleError : styles.titleHelp} role={titleError ? "alert" : undefined}>
          {titleError || (SHARING_ENABLED ? "찍은 장면과 제목은 다른 방문자에게 익명으로 보여요." : "제목은 지금 이 화면에서만 보여요.")}
        </p>
      </form>
      <div className={styles.sheetActions}>
        <button type="button" className={styles.secondary} onClick={onRetake}>
          다시 찍기
        </button>
        <button ref={confirmRef} type="button" className={styles.primary} onClick={submit}>
          이 한 장으로
        </button>
      </div>
    </dialog>
  );
}
