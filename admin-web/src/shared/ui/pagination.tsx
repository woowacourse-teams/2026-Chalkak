"use client";

import styles from "./common-ui.module.css";

interface PaginationProps {
  currentPage: number;
  hasNext: boolean;
  onPageChange: (page: number) => void;
}

export function Pagination({
  currentPage,
  hasNext,
  onPageChange,
}: PaginationProps) {
  return (
    <nav aria-label="페이지 이동" className={styles.pagination}>
      <button
        disabled={currentPage <= 1}
        onClick={() => onPageChange(currentPage - 1)}
        type="button"
      >
        이전
      </button>
      <span aria-current="page">{currentPage} 페이지</span>
      <button
        disabled={!hasNext}
        onClick={() => onPageChange(currentPage + 1)}
        type="button"
      >
        다음
      </button>
    </nav>
  );
}
