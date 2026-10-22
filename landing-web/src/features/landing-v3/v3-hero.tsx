import Image from "next/image";

import styles from "./landing-v3.module.css";

export function V3Hero() {
  return (
    <section className={styles.hero} aria-labelledby="hero-title">
      <div className={`${styles.container} ${styles.heroGrid}`}>
        <div className={styles.heroCopy}>
          <h1 id="hero-title" className={styles.heroTitle}>
            매일 하나의 주제,
            <br />
            각자의 한 장
          </h1>
          <p className={styles.heroLead}>잘 찍은 사진보다, 나만의 시선이 담긴 사진을 나눠요. 오늘의 주제를 내 방식대로 찍어 전시하고, 다른 사람들의 시선을 감상해 보세요.</p>
          <a href="#experience" className={styles.primaryButton}>
            지금 한 장 찍어보기
            <span aria-hidden="true">→</span>
          </a>
        </div>

        <div className={styles.heroVisual}>
          {/* 새 디자인(v2) 히어로와 같은 사진(Pexels 13926637)의 세로판 */}
          <Image src="/hero/capture-sunset-portrait.webp" alt="" fill priority sizes="(max-width: 960px) 100vw, 560px" className={styles.heroImage} />
        </div>
      </div>
    </section>
  );
}
