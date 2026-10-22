import { faqItems } from "@/features/landing/faq";

import styles from "./landing-v3.module.css";

export function V3Faq() {
  return (
    <section id="faq" className={styles.section} aria-labelledby="faq-title">
      <div className={`${styles.container} ${styles.faqGrid}`}>
        <div className={styles.faqHead}>
          <p className={styles.eyebrow}>자주 묻는 질문</p>
          <h2 id="faq-title" className={styles.sectionTitle}>
            궁금한 점이
            <br />
            있나요?
          </h2>
        </div>
        <div className={styles.faqList}>
          {faqItems.map((item, index) => (
            <details key={item.q} className={styles.faqItem} open={index === 0}>
              <summary>
                <span className={styles.faqNumber} aria-hidden="true">
                  {String(index + 1).padStart(2, "0")}
                </span>
                <span className={styles.faqQuestion}>{item.q}</span>
                <span className={styles.faqToggle} aria-hidden="true" />
              </summary>
              <p>{item.a}</p>
            </details>
          ))}
        </div>
      </div>
    </section>
  );
}
