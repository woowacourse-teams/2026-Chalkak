import { SHARING_ENABLED } from "@/features/experience/shared-shot";

import styles from "./landing.module.css";

// 답변은 공통 비즈니스 규칙과 맞춘다: POST-003, POST-002, SIGN-003·005, POST-007·LIKE-001
export const faqItems = [
  {
    q: "하루에 몇 장 올릴 수 있나요?",
    a: "주제 하나에 한 장이에요. 고르고 고른 한 장으로 오늘의 주제에 참여해요.",
  },
  {
    q: "꼭 그날 찍은 사진이어야 하나요?",
    a: "아니요. 주제가 열려 있는 동안이라면 앨범에 있던 사진으로도 참여할 수 있어요.",
  },
  {
    q: "사인은 왜 필요하고, 바꿀 수 있나요?",
    a: "사인은 모든 작품에 함께 보이는 작가 표시예요. 언제든 다시 그릴 수 있고, 바꾸면 지난 작품에도 새 사인이 보여요.",
  },
  {
    q: "가입하지 않아도 볼 수 있나요?",
    a: "전시는 가입 없이 둘러볼 수 있어요. 작품을 올리거나 좋아요를 남기려면 가입이 필요해요.",
  },
  {
    q: "이 페이지에서 찍은 사진은 어디에 저장되나요?",
    a: SHARING_ENABLED
      ? "풍경에서 고른 장면의 위치와 붙인 제목만 익명으로 저장돼, 같은 체험을 한 다른 방문자에게 보여요. 그린 사인은 어디에도 저장되지 않고, 앱의 실제 작품으로 등록되지도 않아요."
      : "찍은 사진과 제목, 그린 사인 모두 어디에도 저장되지 않아요. 체험용으로 지금 보고 있는 화면에서만 보여요.",
  },
];

export function Faq() {
  return (
    <section id="faq" className={styles.section} aria-labelledby="faq-title">
      <div className={`${styles.container} ${styles.faqInner}`}>
        <div>
          <p className={styles.eyebrow}>자주 묻는 질문</p>
          <h2 id="faq-title" className={styles.sectionTitle}>
            궁금한 점이 있나요?
          </h2>
        </div>
        <div className={styles.faqList}>
          {faqItems.map((item) => (
            <details key={item.q} className={styles.faqItem}>
              <summary>{item.q}</summary>
              <p>{item.a}</p>
            </details>
          ))}
        </div>
      </div>
    </section>
  );
}
