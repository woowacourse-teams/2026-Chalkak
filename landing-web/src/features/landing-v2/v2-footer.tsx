import { PRIVACY_URL, TERMS_URL } from "@/shared/store-links";
import { Logo } from "@/shared/ui/logo";

import styles from "./landing-v2.module.css";

export function V2Footer() {
  return (
    <footer className={styles.footer}>
      <div className={`${styles.container} ${styles.footerTop}`}>
        <div>
          <Logo />
          <p className={styles.footerTagline}>매일 하나의 주제, 각자의 한 장</p>
        </div>
        <div className={styles.footerColumns}>
          <nav aria-label="바로가기">
            <p>바로가기</p>
            <a href="#how">사용법</a>
            <a href="#experience">체험하기</a>
            <a href="#faq">자주 묻는 질문</a>
          </nav>
          <nav aria-label="약관">
            <p>약관</p>
            <a href={TERMS_URL} target="_blank" rel="noopener noreferrer">
              이용약관
            </a>
            <a href={PRIVACY_URL} target="_blank" rel="noopener noreferrer">
              개인정보 처리방침
            </a>
          </nav>
        </div>
      </div>
      <div className={`${styles.container} ${styles.footerBottom}`}>
        <p>© 2026 Chalkak</p>
      </div>
      <p className={styles.wordmark} aria-hidden="true">
        CHALKAK
      </p>
    </footer>
  );
}
