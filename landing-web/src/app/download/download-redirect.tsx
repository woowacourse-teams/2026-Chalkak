"use client";

import Link from "next/link";
import { useEffect } from "react";

import { detectPlatform, storeUrlFor } from "@/shared/store-links";
import { LogoMark } from "@/shared/ui/logo";
import { StoreBadges } from "@/shared/ui/store-badges";

import styles from "./download.module.css";

/** QR로 들어온 휴대폰은 맞는 스토어로 보내고, 그 밖의 기기에는 두 스토어를 보여준다. */
export function DownloadRedirect() {
  useEffect(() => {
    const target = storeUrlFor(detectPlatform(navigator.userAgent, navigator.maxTouchPoints));
    if (target) window.location.replace(target);
  }, []);

  return (
    <main className={styles.page}>
      <LogoMark size={64} />
      <h1>찰캌 앱으로 이동하고 있어요</h1>
      <p>자동으로 이동하지 않으면 아래에서 스토어를 골라 주세요.</p>
      <StoreBadges className={styles.badges} />
      <Link href="/" className={styles.home}>
        찰캌 소개 보기
      </Link>
    </main>
  );
}
