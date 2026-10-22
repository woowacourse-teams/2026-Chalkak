import { Noto_Serif_KR } from "next/font/google";

// 전시 도록처럼 보이도록 제목에만 쓰는 세리프. 한글 글리프가 많아 미리 불러오지 않는다.
export const serif = Noto_Serif_KR({
  weight: ["500", "700"],
  subsets: ["latin"],
  variable: "--font-serif",
  display: "swap",
  preload: false,
});
