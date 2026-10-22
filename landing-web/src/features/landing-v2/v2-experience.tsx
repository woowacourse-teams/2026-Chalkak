import { Experience } from "@/features/experience/experience";

import styles from "./landing-v2.module.css";

export function V2Experience() {
  return (
    <section id="experience" className={styles.experienceBand} aria-labelledby="experience-title">
      <div className={styles.container}>
        <div className={styles.experienceHead}>
          <p className={styles.tagLight}>
            <span className={styles.tagDot} />
            체험하기
          </p>
          <h2 id="experience-title" className={styles.experienceTitle}>
            이 풍경에서, 당신의 &lsquo;온기&rsquo;를 찍어보세요
          </h2>
          <p className={styles.experienceLead}>같은 풍경과 주제를 받은 다른 작가들은 어디를 찍었을까요? 한 장 찍고 나면 보여드릴게요.</p>
        </div>
        <div className={styles.experiencePanel}>
          <Experience showSoundToggle={false} />
        </div>
      </div>
    </section>
  );
}
