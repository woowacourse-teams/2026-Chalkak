import type { Metadata } from "next";

import styles from "@/features/landing-v2/landing-v2.module.css";
import { V2Experience } from "@/features/landing-v2/v2-experience";
import { V2Faq } from "@/features/landing-v2/v2-faq";
import { V2Footer } from "@/features/landing-v2/v2-footer";
import { V2Header } from "@/features/landing-v2/v2-header";
import { V2Hero } from "@/features/landing-v2/v2-hero";
import { V2Start } from "@/features/landing-v2/v2-start";
import { V2Steps } from "@/features/landing-v2/v2-steps";
import { V2Why } from "@/features/landing-v2/v2-why";

// 같은 내용의 다른 디자인이라 검색에는 기본 페이지만 노출한다.
export const metadata: Metadata = {
  alternates: { canonical: "/" },
};

export default function LandingV2Page() {
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
