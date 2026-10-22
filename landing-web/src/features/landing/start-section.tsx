import QRCode from "qrcode";

import { siteUrl } from "@/shared/store-links";
import { StoreBadges } from "@/shared/ui/store-badges";

import styles from "./landing.module.css";

export async function StartSection() {
  // 데스크톱 방문자가 휴대폰으로 찍으면 /download가 기기에 맞는 스토어로 보낸다.
  const qr = await QRCode.toString(`${siteUrl()}/download`, {
    type: "svg",
    margin: 0,
    errorCorrectionLevel: "M",
    color: { dark: "#17332D", light: "#00000000" },
  });

  return (
    <section id="start" className={`${styles.section} ${styles.start}`} aria-labelledby="start-title">
      <div className={`${styles.container} ${styles.startInner}`}>
        <div>
          <p className={styles.eyebrowLight}>시작하기</p>
          <h2 id="start-title" className={styles.startTitle}>
            오늘의 주제가
            <br />
            당신의 한 장을 기다려요
          </h2>
          <p className={styles.startLead}>iPhone과 Android에서 무료로 시작할 수 있어요.</p>
          <StoreBadges tone="dark" />
        </div>
        <div className={styles.qrCard}>
          <div className={styles.qr} role="img" aria-label="앱 설치 페이지로 연결되는 QR 코드" dangerouslySetInnerHTML={{ __html: qr }} />
          <p>휴대폰 카메라로 찍으면 기기에 맞는 스토어로 이동해요</p>
        </div>
      </div>
    </section>
  );
}
