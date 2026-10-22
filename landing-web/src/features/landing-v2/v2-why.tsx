import Image from "next/image";
import type { ReactNode } from "react";

import { appHighlights, appHighlightsTitle } from "@/features/landing/app-highlights";
import { appScreenSource, SCREEN_HEIGHT, SCREEN_WIDTH } from "@/features/landing/app-screens";

import { AlbumIcon, CalendarIcon, GridIcon, HeartIcon } from "./icons";
import styles from "./landing-v2.module.css";

const icons: Record<(typeof appHighlights)[number]["id"], ReactNode> = {
  sort: <GridIcon />,
  album: <AlbumIcon />,
  like: <HeartIcon />,
  calendar: <CalendarIcon />,
};

type Point = (typeof appHighlights)[number];

function PointCard({ point }: { point: Point }) {
  return (
    <article className={styles.whyCard}>
      <span className={styles.iconSquare}>{icons[point.id]}</span>
      <h3>{point.title}</h3>
      <p>{point.body}</p>
    </article>
  );
}

export function V2Why() {
  return (
    <section className={styles.section} aria-labelledby="why-title">
      <div className={styles.container}>
        <div className={styles.centerHead}>
          <p className={styles.tag}>
            <span className={styles.tagDot} />
            앱에서는
          </p>
          <h2 id="why-title" className={styles.centerTitle}>
            {appHighlightsTitle[0]}
            <br />
            {appHighlightsTitle[1]}
          </h2>
        </div>

        <div className={styles.whyGrid}>
          <div className={styles.whyColumn}>
            {appHighlights.slice(0, 2).map((point) => (
              <PointCard key={point.id} point={point} />
            ))}
          </div>
          <div className={styles.whyVisual}>
            <div className={styles.whyPhone}>
              <Image src={appScreenSource("display-detail.webp")} alt="작품 상세와 좋아요가 보이는 앱 화면" width={SCREEN_WIDTH} height={SCREEN_HEIGHT} sizes="(max-width: 640px) 60vw, 240px" />
            </div>
          </div>
          <div className={styles.whyColumn}>
            {appHighlights.slice(2).map((point) => (
              <PointCard key={point.id} point={point} />
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
