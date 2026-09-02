"use client";

import styles from "./special-page.module.css";

export default function GlobalError({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <html lang="ko">
      <body>
        <main className={styles.globalError}>
          <p>CHALKAK ADMIN</p>
          <h1>화면을 복구하지 못했습니다.</h1>
          <span>다시 시도하거나 페이지를 새로고침해 주세요.</span>
          <button onClick={reset} type="button">
            다시 시도
          </button>
        </main>
      </body>
    </html>
  );
}
