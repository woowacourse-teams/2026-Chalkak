import type { Metadata } from "next";

import { DownloadRedirect } from "./download-redirect";

export const metadata: Metadata = {
  title: "찰캌 앱 받기",
  description: "기기에 맞는 스토어에서 찰캌을 받아요.",
  robots: { index: false, follow: true },
};

export default function DownloadPage() {
  return <DownloadRedirect />;
}
