"use client";

import { useEffect, useId, useRef, useState, type KeyboardEvent } from "react";
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
  pending?: boolean;
  destructive?: boolean;
  onCancel: () => void;
  onConfirm: (reason: string) => void;
}

export function ConfirmDialog({ open, ...props }: ConfirmDialogProps) {
  return open ? <DialogContent {...props} /> : null;
}

function DialogContent({
  title,
  description,
  confirmLabel,
  reasonField,
  pending = false,
  destructive = false,
  onCancel,
  onConfirm,
}: Omit<ConfirmDialogProps, "open">) {
  const [reason, setReason] = useState("");
  const headingRef = useRef<HTMLHeadingElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);
  const id = useId();
  const normalizedReason = reason.trim();
  const reasonMissing = Boolean(reasonField?.required && !normalizedReason);

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
    if (event.shiftKey && (document.activeElement === first || document.activeElement === headingRef.current)) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  return (
    <div className={styles.dialogLayer}>
      <div
        aria-describedby={id + "-description"}
        aria-labelledby={id + "-title"}
        aria-modal="true"
        className={styles.dialog}
        onKeyDown={handleKeyDown}
        ref={dialogRef}
        role="dialog"
      >
        <h2 id={id + "-title"} ref={headingRef} tabIndex={-1}>{title}</h2>
        <p id={id + "-description"}>{description}</p>
        {reasonField ? (
          <label className={styles.reasonField}>
            <span>{reasonField.label}</span>
            <input
              disabled={pending}
              maxLength={reasonField.maxLength}
              onChange={(event) => setReason(event.target.value)}
              placeholder={reasonField.placeholder}
              required={reasonField.required}
              value={reason}
            />
          </label>
        ) : null}
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
