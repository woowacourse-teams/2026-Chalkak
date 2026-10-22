"use client";

import { useEffect, useRef } from "react";

import { cropBackground, type Rect } from "./camera";
import { SignaturePad } from "./signature-pad";
import type { SignatureStroke } from "./signature";
import styles from "./experience.module.css";

export function SignatureSheet({ mode, signaturePng, strokes, shot, source, onChange, onBack, onConfirm }: { mode: "sign" | "preview"; signaturePng: string; strokes: SignatureStroke[]; shot: Rect; source: string; onChange: (strokes: SignatureStroke[]) => void; onBack: () => void; onConfirm: () => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  useEffect(() => { const dialog = dialogRef.current; if (dialog && !dialog.open) dialog.showModal(); return () => dialog?.close(); }, []);
  return <dialog ref={dialogRef} className={styles.sheet} aria-labelledby="signature-title" onCancel={(event) => { event.preventDefault(); onBack(); }}>
    {mode === "sign" ? <>
      <h2 id="signature-title" className={styles.signatureTitle}>작가님의<br />사인을 그려주세요</h2>
      <p className={styles.signatureDescription}>모든 사진에 함께할 사인이에요.<br />자유롭게 남겨주시고, 실제 서명은 피해 주세요.</p>
      <SignaturePad strokes={strokes} onChange={onChange} />
      <button type="button" className={styles.primary} disabled={!strokes.length} onClick={onConfirm}>이 사인으로 할래요</button>
    </> : <>
      <h2 id="signature-title" className={styles.signatureTitle}>이렇게 보여요</h2>
      <div className={styles.signaturePreview} style={{ backgroundImage: `url(${source})`, ...cropBackground(shot) }} role="img" aria-label="사인이 얹힌 방금 찍은 한 장">
        {signaturePng && (
          // PNG data URL은 오프스크린 캔버스에서 생성한 체험 전용 이미지라 next/image 최적화 대상이 아니다.
          // eslint-disable-next-line @next/next/no-img-element
          <img src={signaturePng} alt="내 사인" />
        )}
      </div>
      <div className={styles.sheetActions}><button type="button" className={styles.secondary} onClick={onBack}>다시 그리기</button><button type="button" className={styles.primary} onClick={onConfirm}>전시하기</button></div>
    </>}
  </dialog>;
}
