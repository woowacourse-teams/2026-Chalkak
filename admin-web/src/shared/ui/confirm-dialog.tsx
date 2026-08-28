"use client";

import { useEffect, useRef, useState } from "react";

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

export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  reasonField,
  pending = false,
  destructive = false,
  onCancel,
  onConfirm,
}: ConfirmDialogProps) {
  const [reason, setReason] = useState("");
  const initialFocusRef = useRef<HTMLInputElement | HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    initialFocusRef.current?.focus();
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !pending) {
        setReason("");
        onCancel();
      }
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [onCancel, open, pending]);

  if (!open) {
    return null;
  }

  const normalizedReason = reason.trim();
  const reasonMissing = Boolean(reasonField?.required && !normalizedReason);

  return (
    <div className={styles.dialogLayer}>
      <div
        aria-describedby="confirm-dialog-description"
        aria-labelledby="confirm-dialog-title"
        aria-modal="true"
        className={styles.dialog}
        role="dialog"
      >
        <p className={styles.dialogEyebrow}>작업 확인</p>
        <h2 id="confirm-dialog-title">{title}</h2>
        <p id="confirm-dialog-description">{description}</p>
        {reasonField ? (
          <label className={styles.reasonField}>
            <span>{reasonField.label}</span>
            <input
              maxLength={reasonField.maxLength}
              onChange={(event) => setReason(event.target.value)}
              placeholder={reasonField.placeholder}
              ref={initialFocusRef as React.RefObject<HTMLInputElement>}
              required={reasonField.required}
              value={reason}
            />
          </label>
        ) : null}
        <div className={styles.dialogActions}>
          <button
            disabled={pending}
            onClick={() => {
              setReason("");
              onCancel();
            }}
            ref={
              reasonField
                ? undefined
                : (initialFocusRef as React.RefObject<HTMLButtonElement>)
            }
            type="button"
          >
            취소
          </button>
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
