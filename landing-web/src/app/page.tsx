import styles from "@/features/landing-v2/landing-v2.module.css";
import { V2Experience } from "@/features/landing-v2/v2-experience";
import { V2Faq } from "@/features/landing-v2/v2-faq";
import { V2Footer } from "@/features/landing-v2/v2-footer";
import { V2Header } from "@/features/landing-v2/v2-header";
import { V2Hero } from "@/features/landing-v2/v2-hero";
import { V2Start } from "@/features/landing-v2/v2-start";
import { V2Steps } from "@/features/landing-v2/v2-steps";
import { V2Why } from "@/features/landing-v2/v2-why";

// 시안 중 2안(새 디자인)으로 확정했다. 예전 시안 주소 /v2, /v3는 next.config.ts에서 이 페이지로 보낸다.
export default function LandingPage() {
  return (
    <div className={styles.page}>
      <a href="#main" className="skip-link">
        본문으로 건너뛰기
      </a>
      <V2Header />
      <main id="main">
        <div id="top" />
        <V2Hero />
        <V2Steps />
        <V2Experience />
        <V2Why />
        <V2Faq />
        <V2Start />
      </main>
      <V2Footer />
    </div>
  );
}
