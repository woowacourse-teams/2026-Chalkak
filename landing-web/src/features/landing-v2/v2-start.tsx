import Image from "next/image";
import QRCode from "qrcode";

import { siteUrl } from "@/shared/store-links";
import { StoreBadges } from "@/shared/ui/store-badges";

import styles from "./landing-v2.module.css";

export async function V2Start() {
  // 데스크톱 방문자가 휴대폰으로 찍으면 /download가 기기에 맞는 스토어로 보낸다.
  const qr = await QRCode.toString(`${siteUrl()}/download`, {
    type: "svg",
    margin: 0,
    errorCorrectionLevel: "M",
    color: { dark: "#0F172A", light: "#00000000" },
  });

  return (
    <section id="start" className={styles.startSection} aria-labelledby="start-title">
      <div className={styles.container}>
        <div className={styles.startCard}>
          {/* Pexels 28436791(Pexels 라이선스). 휴대폰이 카드 위쪽 가운데에 오게 맞춰 제목·QR과 겹치지 않게 한다. */}
          <div className={styles.startPhoto}>
            <Image src="/hero/capture-hands.webp" alt="" fill sizes="(max-width: 1200px) 100vw, 1168px" className={styles.startImage} />
          </div>
          <div className={styles.startShade} />
          <div className={styles.startContent}>
            <p className={styles.tagLight}>
              <span className={styles.tagDot} />
              시작하기
            </p>
            <h2 id="start-title" className={styles.startTitle}>
              오늘의 주제가
              <br />
              당신의 한 장을 기다려요
            </h2>
            <p className={styles.startLead}>iPhone과 Android에서 무료로 시작할 수 있어요.</p>
            <StoreBadges tone="dark" className={styles.startBadges} />
          </div>
          <div className={styles.startQr}>
            <div className={styles.qr} role="img" aria-label="앱 설치 페이지로 연결되는 QR 코드" dangerouslySetInnerHTML={{ __html: qr }} />
            <p>휴대폰 카메라로 찍으면 기기에 맞는 스토어로 이동해요</p>
          </div>
        </div>
      </div>
    </section>
  );
}
