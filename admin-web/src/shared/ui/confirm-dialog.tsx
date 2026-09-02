"use client";

import { useEffect, useId, useRef, useState, useSyncExternalStore, type KeyboardEvent } from "react";
import styles from "./common-ui.module.css";

interface ReasonField {
  label: string;
  placeholder?: string;
  required?: boolean;
  maxLength?: number;
}

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  reasonField?: ReasonField;
  error?: string | null;
  pending?: boolean;
  destructive?: boolean;
  onCancel: () => void;
  onConfirm: (reason: string) => void;
}

function subscribeToVisualViewport(onChange: () => void) {
  const viewport = window.visualViewport;
  viewport?.addEventListener("resize", onChange);
  viewport?.addEventListener("scroll", onChange);
  return () => {
    viewport?.removeEventListener("resize", onChange);
    viewport?.removeEventListener("scroll", onChange);
  };
}

function getVisualViewportSnapshot() {
  const viewport = window.visualViewport;
  // A primitive snapshot stays stable until either browser value changes.
  return viewport ? `${viewport.height}:${viewport.offsetTop}` : null;
}

function getServerViewportSnapshot() {
  return null;
}

export function ConfirmDialog({ open, ...props }: ConfirmDialogProps) {
  return open ? <DialogContent {...props} /> : null;
}

function DialogContent({
  title,
  description,
  confirmLabel,
  reasonField,
  error,
  pending = false,
  destructive = false,
  onCancel,
  onConfirm,
}: Omit<ConfirmDialogProps, "open">) {
  const [reason, setReason] = useState("");
  const headingRef = useRef<HTMLHeadingElement>(null);
  const errorRef = useRef<HTMLParagraphElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);
  const id = useId();
  const normalizedReason = reason.trim();
  const reasonMissing = Boolean(reasonField?.required && !normalizedReason);
  const viewport = useSyncExternalStore(
    subscribeToVisualViewport,
    getVisualViewportSnapshot,
    getServerViewportSnapshot,
  );
  const [viewportHeight, viewportTop] = viewport?.split(":").map(Number) ?? [];

  useEffect(() => {
    const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const keepFocusInside = (event: FocusEvent) => {
      if (event.target instanceof Node && !dialogRef.current?.contains(event.target)) {
        headingRef.current?.focus();
      }
    };
    document.addEventListener("focusin", keepFocusInside);
    headingRef.current?.focus();
    return () => {
      document.removeEventListener("focusin", keepFocusInside);
      document.body.style.overflow = previousOverflow;
      previouslyFocused?.focus();
    };
  }, []);

  useEffect(() => {
    if (pending) headingRef.current?.focus();
  }, [pending]);

  useEffect(() => {
    if (error && !pending) errorRef.current?.focus();
  }, [error, pending]);

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape") {
      event.preventDefault();
      if (!pending) onCancel();
      return;
    }
    if (event.key !== "Tab") return;
    const controls = Array.from(dialogRef.current?.querySelectorAll<HTMLElement>(
      'button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), a[href], [tabindex="0"]',
    ) ?? []);
    const first = controls[0];
    const last = controls.at(-1);
    if (!first || !last) {
      event.preventDefault();
      return;
    }
    if (event.shiftKey && (document.activeElement === first || document.activeElement === headingRef.current || document.activeElement === errorRef.current)) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  return (
    <div
      className={styles.dialogLayer}
      style={viewport === null ? undefined : { top: viewportTop, height: viewportHeight }}
    >
      <div
        aria-describedby={id + "-description" + (error ? " " + id + "-error" : "")}
        aria-labelledby={id + "-title"}
        aria-modal="true"
        className={styles.dialog}
        onKeyDown={handleKeyDown}
        ref={dialogRef}
        role="dialog"
      >
        <div className={styles.dialogBody}>
          <h2 id={id + "-title"} ref={headingRef} tabIndex={-1}>{title}</h2>
          <p className={styles.dialogDescription} id={id + "-description"}>{description}</p>
          {error ? (
            <p className={styles.dialogError} id={id + "-error"} ref={errorRef} role="alert" tabIndex={-1}>{error}</p>
          ) : null}
          {reasonField ? (
            <label className={styles.reasonField}>
              <span>{reasonField.label}</span>
              <textarea
                disabled={pending}
                maxLength={reasonField.maxLength}
                onChange={(event) => setReason(event.target.value)}
                placeholder={reasonField.placeholder}
                required={reasonField.required}
                rows={3}
                value={reason}
              />
            </label>
          ) : null}
        </div>
        <div className={styles.dialogActions}>
          <button disabled={pending} onClick={onCancel} type="button">취소</button>
          <button
            className={destructive ? styles.destructiveButton : styles.primaryButton}
            disabled={pending || reasonMissing}
            onClick={() => onConfirm(normalizedReason)}
            type="button"
          >
            {pending ? "처리 중…" : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
