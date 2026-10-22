import { Experience } from "@/features/experience/experience";
import { onggiScene } from "@/features/experience/scene";

import styles from "./landing-v3.module.css";

export function V3Experience() {
  return (
    <section id="experience" className={styles.section} aria-labelledby="experience-title">
      <div className={styles.container}>
        <div className={styles.splitHead}>
          <div>
            <p className={styles.eyebrow}>체험하기</p>
            <h2 id="experience-title" className={styles.sectionTitle}>
              이 풍경에서, 당신의
              <br />
              &lsquo;{onggiScene.topic}&rsquo;를 찍어보세요
            </h2>
          </div>
          <p className={styles.sectionLead}>같은 풍경과 주제를 받은 다른 작가들은 어디를 찍었을까요? 한 장 찍고 나면 보여드릴게요.</p>
        </div>
        <div className={styles.experienceRoom}>
          <Experience showSoundToggle={false} />
        </div>
      </div>
    </section>
  );
}
