import type { Metadata } from "next";

import { serif } from "@/features/landing-v3/fonts";
import styles from "@/features/landing-v3/landing-v3.module.css";
import { V3Experience } from "@/features/landing-v3/v3-experience";
import { V3Faq } from "@/features/landing-v3/v3-faq";
import { V3Footer } from "@/features/landing-v3/v3-footer";
import { V3Header } from "@/features/landing-v3/v3-header";
import { V3Hero } from "@/features/landing-v3/v3-hero";
import { V3Route } from "@/features/landing-v3/v3-route";
import { V3Ticket } from "@/features/landing-v3/v3-ticket";
import { V3Why } from "@/features/landing-v3/v3-why";

// 같은 내용의 다른 디자인이라 검색에는 기본 페이지만 노출한다.
export const metadata: Metadata = {
  alternates: { canonical: "/" },
};

export default function LandingV3Page() {
  return (
    <div className={`${styles.page} ${serif.variable}`}>
      <a href="#main" className="skip-link">
        본문으로 건너뛰기
      </a>
      <V3Header />
      <main id="main">
        <div id="top" />
        <V3Hero />
        <V3Route />
        <V3Experience />
        <V3Why />
        <V3Faq />
        <V3Ticket />
      </main>
      <V3Footer />
    </div>
  );
}
