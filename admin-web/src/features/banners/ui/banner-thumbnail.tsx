"use client";
import Image from "next/image";
import { useState } from "react";
import { isHttpsUrl } from "../model/banner";
import styles from "./banners.module.css";

export function BannerThumbnail({ url, title }: { url: string; title: string }) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  return <div className={styles.thumbnail}>
    {isHttpsUrl(url) && failedUrl !== url
      ? <Image src={url} alt={`${title || "배너"} 썸네일`} width={480} height={240} unoptimized referrerPolicy="no-referrer" onError={() => setFailedUrl(url)} />
      : <span>{url ? "이미지를 불러올 수 없습니다" : "썸네일 미리보기"}</span>}
  </div>;
}
