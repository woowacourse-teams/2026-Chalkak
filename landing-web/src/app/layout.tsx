import type { Metadata, Viewport } from "next";
import { Nanum_Pen_Script } from "next/font/google";
import localFont from "next/font/local";
import type { ReactNode } from "react";

import { siteUrl } from "@/shared/store-links";

import "./globals.css";

// scripts/subset-fonts.py로 앱 번들의 Pretendard를 줄인 파일
const pretendard = localFont({
  src: [
    { path: "./fonts/Pretendard-400.woff2", weight: "400", style: "normal" },
    { path: "./fonts/Pretendard-600.woff2", weight: "600", style: "normal" },
    { path: "./fonts/Pretendard-700.woff2", weight: "700", style: "normal" },
  ],
  variable: "--font-sans",
  display: "swap",
});

// 작품 사인처럼 손글씨가 필요한 곳에만 쓴다. 한글 글리프가 많아 미리 불러오지 않는다.
const handwriting = Nanum_Pen_Script({
  weight: "400",
  subsets: ["latin"],
  variable: "--font-hand",
  display: "swap",
  preload: false,
});

const title = "찰캌 — 매일 하나의 주제, 각자의 한 장";
const description =
  "잘 찍은 사진보다, 나만의 시선이 담긴 사진을 나누는 일상 사진 전시 공간. 오늘의 주제를 찍어 전시하고, 같은 주제를 다른 사람들은 어떻게 담았는지 감상해 보세요.";

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl()),
  title,
  description,
  applicationName: "찰캌",
  alternates: { canonical: "/" },
  openGraph: {
    type: "website",
    locale: "ko_KR",
    siteName: "찰캌",
    title,
    description,
    url: "/",
  },
  twitter: {
    card: "summary_large_image",
    title,
    description,
  },
  itunes: {
    appId: "6809104229",
  },
};

export const viewport: Viewport = {
  themeColor: "#f7f6f3",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ko" className={`${pretendard.variable} ${handwriting.variable}`}>
      <body>{children}</body>
    </html>
  );
}
