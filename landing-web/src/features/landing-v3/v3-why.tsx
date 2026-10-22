import Image from "next/image";

import { appHighlights, appHighlightsTitle } from "@/features/landing/app-highlights";
import { appScreenSource, SCREEN_HEIGHT, SCREEN_WIDTH } from "@/features/landing/app-screens";

import styles from "./landing-v3.module.css";

export function V3Why() {
  return (
    <section className={`${styles.section} ${styles.sectionSunken}`} aria-labelledby="why-title">
      <div className={`${styles.container} ${styles.whyGrid}`}>
        <div className={styles.whyScreen}>
          <Image src={appScreenSource("display-detail.webp")} alt="작품 상세와 좋아요가 보이는 앱 화면" width={SCREEN_WIDTH} height={SCREEN_HEIGHT} sizes="(max-width: 640px) 60vw, 260px" />
        </div>
        <div>
          <p className={styles.eyebrow}>앱에서는</p>
          <h2 id="why-title" className={styles.sectionTitle}>
            {appHighlightsTitle[0]}
            <br />
            {appHighlightsTitle[1]}
          </h2>
          <ul className={styles.whyList}>
            {appHighlights.map((point) => (
              <li key={point.id} className={styles.whyItem}>
                <h3>{point.title}</h3>
                <p>{point.body}</p>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  );
}
