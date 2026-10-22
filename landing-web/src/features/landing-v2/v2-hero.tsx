import Image from "next/image";

import { StoreBadges } from "@/shared/ui/store-badges";

import { ArrowUpRight } from "./icons";
import styles from "./landing-v2.module.css";

export function V2Hero() {
  return (
    <section className={styles.hero} aria-labelledby="hero-title">
      {/* Pexels 13926637(Pexels 라이선스). 넓은 화면용은 왼쪽을, 모바일용은 위쪽 하늘을 늘려 휴대폰이 제목과 겹치지 않게 했다. */}
      <Image src="/hero/capture-sunset.webp" alt="" fill priority sizes="(max-width: 640px) 1px, 100vw" className={`${styles.heroImage} ${styles.heroImageWide}`} />
      <Image src="/hero/capture-sunset-portrait.webp" alt="" fill priority sizes="(max-width: 640px) 100vw, 1px" className={`${styles.heroImage} ${styles.heroImagePortrait}`} />
      <div className={styles.heroShade} />

      <div className={`${styles.container} ${styles.heroInner}`}>
        <div className={styles.heroCopy}>
          <h1 id="hero-title" className={styles.heroTitle}>
            매일 하나의 주제,
            <br />
            각자의 한 장
          </h1>
          <p className={styles.heroLead}>잘 찍은 사진보다, 나만의 시선이 담긴 사진을 나눠요. 오늘의 주제를 내 방식대로 찍어 전시하고, 다른 사람들의 시선을 감상해 보세요.</p>
          <a href="#experience" className={styles.pillButton}>
            지금 한 장 찍어보기
            <span className={styles.pillArrow}>
              <ArrowUpRight size={16} />
            </span>
          </a>
          {/* 체험 없이 바로 설치하려는 방문자를 위해 첫 화면에도 스토어 배지를 둔다. 방문자 기기의 스토어가 앞에 온다. */}
          <StoreBadges tone="dark" className={styles.heroBadges} />
        </div>
      </div>
    </section>
  );
}
