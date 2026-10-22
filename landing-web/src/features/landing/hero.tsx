import Image from "next/image";

import { StoreBadges } from "@/shared/ui/store-badges";

import { appScreenSource, SCREEN_HEIGHT, SCREEN_WIDTH } from "./app-screens";
import styles from "./landing.module.css";

export function Hero() {
  return (
    <section className={styles.hero} aria-labelledby="hero-title">
      <div className={`${styles.container} ${styles.heroInner}`}>
        <div className={styles.heroCopy}>
          <p className={styles.eyebrow}>찰캌 · 일상 사진 전시 공간</p>
          <h1 id="hero-title" className={styles.heroTitle}>
            매일 하나의 주제,
            <br />
            각자의 한 장
          </h1>
          <p className={styles.heroLead}>
            잘 찍은 사진보다, 나만의 시선이 담긴 사진을 나눠요.
            <br />
            오늘의 주제를 내 방식대로 찍어 전시하고, 같은 주제를 다른 사람들은 어떻게 담았는지 감상해 보세요.
          </p>
          <div className={styles.heroActions}>
            <a href="#experience" className={styles.primaryLink}>
              지금 한 장 찍어보기
            </a>
            <StoreBadges />
          </div>
        </div>

        <div className={styles.heroVisual} aria-label="찰캌 앱 화면">
          <div className={`${styles.phone} ${styles.heroPhoneLeft}`}>
            <Image src={appScreenSource("display-feed.webp")} alt="같은 주제의 다양한 사진을 감상하는 찰캌 전시 화면" width={SCREEN_WIDTH} height={SCREEN_HEIGHT} sizes="(max-width: 640px) 1px, 210px" />
          </div>
          <div className={`${styles.phone} ${styles.heroPhoneCenter}`}>
            <Image src={appScreenSource("today.webp")} alt="오늘의 주제를 받아 내 시선으로 사진을 남기는 찰캌 홈 화면" width={SCREEN_WIDTH} height={SCREEN_HEIGHT} priority sizes="(max-width: 640px) 240px, 260px" />
          </div>
          <div className={`${styles.phone} ${styles.heroPhoneRight}`}>
            <Image src={appScreenSource("display-detail.webp")} alt="사진의 사인을 보고 좋아요를 남기는 찰캌 작품 상세 화면" width={SCREEN_WIDTH} height={SCREEN_HEIGHT} sizes="(max-width: 960px) 1px, 210px" />
          </div>
        </div>
      </div>
    </section>
  );
}
