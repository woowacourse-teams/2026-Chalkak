import Image from "next/image";

import { appScreenSource, SCREEN_HEIGHT, SCREEN_WIDTH } from "./app-screens";
import styles from "./landing.module.css";

export type Step = {
  id: string;
  title: string;
  body: string;
  /** public/app-screens 아래의 최신 앱 직접 캡처 */
  screen: string;
  screenAlt: string;
};

export const guideSteps: Step[] = [
  {
    id: "sign",
    title: "나만의 사인을 그려요",
    body: "가입할 때 한 번, 모든 작품에 함께할 사인을 그려요. 사인은 언제든 다시 그릴 수 있어요.",
    screen: "signature.webp",
    screenAlt: "사인을 그리는 앱 화면",
  },
  {
    id: "today",
    title: "오늘의 주제를 받아요",
    body: "매일 하나의 주제가 열려요. 정해진 시간에 알림으로도 알려 드려요.",
    screen: "today.webp",
    screenAlt: "오늘의 주제가 보이는 앱 홈 화면",
  },
  {
    id: "upload",
    title: "나만의 한 장을 올려요",
    body: "주제에 맞는 순간을 바로 찍거나, 앨범에서 골라 전시해요.",
    screen: "upload.webp",
    screenAlt: "사진을 올리는 앱 화면",
  },
  {
    id: "display",
    title: "다른 시선을 감상해요",
    body: "같은 주제를 다른 사람들은 어떻게 담았는지 최신순·인기순·랜덤순으로 둘러봐요.",
    screen: "display-feed.webp",
    screenAlt: "같은 주제의 사진들이 모인 전시 화면",
  },
  {
    id: "keep",
    title: "좋아요를 남기고, 기록을 모아요",
    body: "마음에 드는 작품에 좋아요를 남겨요. 내가 참여한 날은 달력에 모여 다시 꺼내볼 수 있어요.",
    screen: "record.webp",
    screenAlt: "참여한 날의 사진이 모인 기록 달력 화면",
  },
];

export function AppGuide() {
  return (
    <section id="how" className={styles.section} aria-labelledby="how-title">
      <div className={styles.container}>
        <p className={styles.eyebrow}>사용법</p>
        <h2 id="how-title" className={styles.sectionTitle}>
          앱에서는 매일, 당신의 하루가 풍경이 돼요
        </h2>
        <p className={styles.sectionLead}>방금 체험한 흐름 그대로예요. 다섯 단계면 오늘의 작가가 될 수 있어요.</p>

        <ol className={styles.guide}>
          {guideSteps.map((step, index) => (
            <li key={step.id} className={styles.guideStep}>
              <div className={styles.phone}>
                <Image src={appScreenSource(step.screen)} alt={step.screenAlt} width={SCREEN_WIDTH} height={SCREEN_HEIGHT} sizes="(max-width: 640px) 70vw, 220px" />
              </div>
              <div className={styles.guideText}>
                <span className={styles.guideNumber}>{index + 1}</span>
                <h3>{step.title}</h3>
                <p>{step.body}</p>
              </div>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}
