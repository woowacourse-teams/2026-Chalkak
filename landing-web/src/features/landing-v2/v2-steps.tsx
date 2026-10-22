import Image from "next/image";

import { guideSteps } from "@/features/landing/app-guide";
import { appScreenSource, SCREEN_HEIGHT, SCREEN_WIDTH } from "@/features/landing/app-screens";

import styles from "./landing-v2.module.css";

export function V2Steps() {
  return (
    <section id="how" className={`${styles.section} ${styles.sectionTinted}`} aria-labelledby="how-title">
      <div className={styles.container}>
        <div className={styles.centerHead}>
          <p className={styles.tag}>
            <span className={styles.tagDot} />
            사용법
          </p>
          <h2 id="how-title" className={styles.centerTitle}>
            찰캌에서 작가가 되어보세요
          </h2>
          <p className={styles.centerLead}>사인부터 기록까지, 찰캌의 하루를 실제 앱 화면으로 보여드릴게요.</p>
        </div>

        <ol className={styles.steps}>
          {guideSteps.map((step, index) => (
            <li key={step.id} className={styles.step}>
              <div className={styles.stepVisual}>
                <Image src={appScreenSource(step.screen)} alt={step.screenAlt} width={SCREEN_WIDTH} height={SCREEN_HEIGHT} sizes="(max-width: 640px) 52vw, 170px" />
              </div>
              <p className={styles.stepNumber}>{String(index + 1).padStart(2, "0")}</p>
              <h3>{step.title}</h3>
              <p className={styles.stepBody}>{step.body}</p>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}
