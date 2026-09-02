"use client";

import Image from "next/image";
import { useState } from "react";

import styles from "./posts.module.css";

interface PostThumbnailProps {
  alt: string;
  src?: string | null;
  detail?: boolean;
}

export function PostThumbnail({
  alt,
  src,
  detail = false,
}: PostThumbnailProps) {
  const [failed, setFailed] = useState(false);

  return (
    <div className={detail ? styles.detailImage : styles.thumbnail}>
      {src && !failed ? (
        <Image
          alt={alt}
          fill
          loading={detail ? "eager" : "lazy"}
          onError={() => setFailed(true)}
          sizes={detail ? "(max-width: 760px) 100vw, 55vw" : "76px"}
          src={src}
          unoptimized
        />
      ) : (
        <span role="img" aria-label="이미지를 불러올 수 없음">
          이미지 없음
        </span>
      )}
    </div>
  );
}
