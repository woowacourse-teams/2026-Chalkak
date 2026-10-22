import Image from "next/image";

import { guideSteps } from "@/features/landing/app-guide";
import { appScreenSource, SCREEN_HEIGHT, SCREEN_WIDTH } from "@/features/landing/app-screens";

import styles from "./landing-v3.module.css";

// 단계 문구는 기본 디자인의 사용법을 그대로 쓴다.

export function V3Route() {
  return (
    <section id="how" className={`${styles.section} ${styles.sectionSunken}`} aria-labelledby="how-title">
      <div className={styles.container}>
        <div className={styles.splitHead}>
          <div>
            <p className={styles.eyebrow}>사용법</p>
            <h2 id="how-title" className={styles.sectionTitle}>
              찰캌에서
              <br />
              작가가 되어보세요
            </h2>
          </div>
          <p className={styles.sectionLead}>사인부터 기록까지, 찰캌의 하루를 실제 앱 화면으로 보여드릴게요.</p>
        </div>

        <ol className={styles.route}>
          {guideSteps.map((step, index) => (
            <li key={step.id} className={styles.routeItem}>
              <p className={styles.routeTag}>
                {String(index + 1).padStart(2, "0")}
              </p>
              <div className={styles.routeScreen}>
                <Image src={appScreenSource(step.screen)} alt={step.screenAlt} width={SCREEN_WIDTH} height={SCREEN_HEIGHT} sizes="(max-width: 640px) 56vw, 180px" />
              </div>
              <h3 className={styles.routeTitle}>{step.title}</h3>
              <p className={styles.routeBody}>{step.body}</p>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}
