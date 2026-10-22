"use client";

import Image from "next/image";

import { APP_STORE_URL, detectPlatform, PLAY_STORE_URL, type Platform } from "@/shared/store-links";
import { useClientValue } from "@/shared/use-client-value";

import styles from "./store-badges.module.css";

const BADGE_HEIGHT = 48;

const badges = {
  ios: { href: APP_STORE_URL, src: "/badges/app-store-ko.svg", alt: "App Store에서 다운로드", width: 156 },
  android: { href: PLAY_STORE_URL, src: "/badges/google-play-ko.png", alt: "Google Play에서 다운로드", width: 162 },
} as const;

/** 방문자 기기의 스토어를 앞에 둔다. 데스크톱에서는 App Store, Google Play 순서다. */
export function StoreBadges({ className, tone = "light" }: { className?: string; tone?: "light" | "dark" }) {
  const platform = useClientValue<Platform>(() => detectPlatform(navigator.userAgent, navigator.maxTouchPoints), "other");
  const order = platform === "android" ? (["android", "ios"] as const) : (["ios", "android"] as const);

  return (
    <div className={[styles.badges, className].filter(Boolean).join(" ")} data-tone={tone}>
      {order.map((key) => {
        const badge = badges[key];
        return (
          <a key={key} className={styles.badge} href={badge.href} target="_blank" rel="noopener noreferrer" data-store={key}>
            <Image src={badge.src} alt={badge.alt} width={badge.width} height={BADGE_HEIGHT} unoptimized />
          </a>
        );
      })}
    </div>
  );
}
