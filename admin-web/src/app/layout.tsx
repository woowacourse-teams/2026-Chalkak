import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Chalkak Admin",
  description: "Chalkak 서비스 운영을 위한 관리자 웹",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
