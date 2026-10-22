import type { SceneFrame } from "./camera";

export type ArtistShot = SceneFrame & {
  id: string;
  /** 작가가 붙인 한 줄 해석 */
  line: string;
  sign: string;
  likes: number;
  /** 최신순 정렬용. 몇 분 전에 전시됐는지 */
  minutesAgo: number;
};

/** 체험용 풍경. 원본은 AI로 만든 수채화 풍경을 4배 업스케일한 이미지다. */
export const onggiScene = {
  topic: "온기",
  intro: "이 골목 어딘가에 당신만의 '온기'가 있어요.",
  alt: "해 질 녘 바닷가 언덕 마을의 골목 풍경",
  size: { width: 7096, height: 3548 },
  sources: {
    preview: "/scene/onggi-1600.webp",
    compact: "/scene/onggi-2x.webp",
    full: "/scene/onggi-4x.webp",
  },
  /** 체험을 시작할 때 뷰파인더가 처음 비추는 곳 */
  start: { x: 0.5, y: 0.5 },
} as const;

/** 결과 화면에 내 작품과 함께 보여 주는 예시 작품 */
export const onggiShots: ArtistShot[] = [
  { id: "sky", cx: 0.595, cy: 0.32, h: 0.6, line: "하루를 데운 하늘", sign: "하늘", likes: 128, minutesAgo: 4 },
  { id: "window", cx: 0.15, cy: 0.17, h: 0.32, line: "누군가 기다리는 창", sign: "jiwoo", likes: 212, minutesAgo: 9 },
  { id: "laundry", cx: 0.38, cy: 0.25, h: 0.3, line: "햇볕 냄새 나는 이불", sign: "몽", likes: 87, minutesAgo: 15 },
  { id: "pot", cx: 0.09, cy: 0.58, h: 0.36, line: "김이 오르는 저녁", sign: "S.", likes: 164, minutesAgo: 21 },
  { id: "cats", cx: 0.3, cy: 0.46, h: 0.24, line: "서로 기대 앉은 둘", sign: "나린", likes: 301, minutesAgo: 26 },
  { id: "vending", cx: 0.71, cy: 0.64, h: 0.36, line: "따뜻한 캔 하나", sign: "eun", likes: 73, minutesAgo: 33 },
  { id: "hands", cx: 0.53, cy: 0.69, h: 0.32, line: "꼭 잡은 손", sign: "도토리", likes: 245, minutesAgo: 38 },
  { id: "bungeo", cx: 0.86, cy: 0.6, h: 0.34, line: "봉투 속 붕어빵", sign: "kim", likes: 119, minutesAgo: 44 },
  { id: "puddle", cx: 0.28, cy: 0.88, h: 0.24, line: "발밑에 고인 노을", sign: "솔", likes: 96, minutesAgo: 52 },
  { id: "dog", cx: 0.89, cy: 0.6, h: 0.8, line: "가로등 아래 단잠", sign: "yuna", likes: 188, minutesAgo: 58 },
  { id: "sea", cx: 0.66, cy: 0.38, h: 0.28, line: "바다에 번진 저녁빛", sign: "봄", likes: 64, minutesAgo: 63 },
  { id: "stool", cx: 0.2, cy: 0.68, h: 0.3, line: "누군가를 기다리는 의자", sign: "J", likes: 142, minutesAgo: 71 },
  { id: "street", cx: 0.5, cy: 0.5, h: 1, line: "다 같이 맞는 저녁", sign: "다온", likes: 57, minutesAgo: 80 },
];
