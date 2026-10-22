import { faqItems } from "@/features/landing/faq";

import styles from "./landing-v2.module.css";

export function V2Faq() {
  return (
    <section id="faq" className={styles.section} aria-labelledby="faq-title">
      <div className={styles.container}>
        <div className={styles.centerHead}>
          <p className={styles.tag}>
            <span className={styles.tagDot} />
            자주 묻는 질문
          </p>
          <h2 id="faq-title" className={styles.centerTitle}>
            궁금한 점이 있나요?
          </h2>
        </div>
        <div className={styles.faqList}>
          {faqItems.map((item, index) => (
            <details key={item.q} className={styles.faqItem} open={index === 0}>
              <summary>
                {item.q}
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
