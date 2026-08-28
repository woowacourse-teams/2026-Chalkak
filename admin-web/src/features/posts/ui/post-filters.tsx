"use client";

import type { FormEvent } from "react";

import type { AdminPostFilters } from "../api/post-api";
import {
  dateEndInstant,
  dateStartInstant,
  instantDate,
} from "../model/post-filter-query";
import styles from "./posts.module.css";

interface PostFiltersProps {
  filters: AdminPostFilters;
  authorOptions: ReadonlyArray<FilterOption>;
  topicOptions: ReadonlyArray<FilterOption>;
  onApply: (patch: Partial<AdminPostFilters>) => void;
  onReset: () => void;
}

export interface FilterOption {
  label: string;
  value: string;
}

export function PostFilters({
  filters,
  authorOptions,
  topicOptions,
  onApply,
  onReset,
}: PostFiltersProps) {
  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const value = (name: string) => String(data.get(name) ?? "").trim();

    onApply({
      status: (value("status") || undefined) as AdminPostFilters["status"],
      topicId: value("topicId") || undefined,
      topicDate: value("topicDate") || undefined,
      userId: value("userId") || undefined,
      createdAtFrom: dateStartInstant(value("createdAtFrom")),
      createdAtTo: dateEndInstant(value("createdAtTo")),
      sort: value("sort") as AdminPostFilters["sort"],
      pageSize: Number(value("pageSize")),
      page: 1,
    });
  };

  return (
    <form className={styles.filters} onSubmit={submit}>
      <div className={styles.filterGrid}>
        <label>
          <span>검수 상태</span>
          <select
            defaultValue={filters.status ?? ""}
            key={filters.status}
            name="status"
          >
            <option value="PENDING">검수 대기</option>
            <option value="APPROVED">승인</option>
            <option value="REJECTED">거절</option>
          </select>
        </label>
        <label>
          <span>주제</span>
          <select
            defaultValue={filters.topicId ?? ""}
            key={filters.topicId}
            name="topicId"
          >
            <option value="">모든 주제</option>
            {topicOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>작성자</span>
          <select
            defaultValue={filters.userId ?? ""}
            key={filters.userId}
            name="userId"
          >
            <option value="">모든 작성자</option>
            {authorOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>주제 날짜</span>
          <input
            defaultValue={filters.topicDate ?? ""}
            key={filters.topicDate}
            name="topicDate"
            type="date"
          />
        </label>
        <label>
          <span>등록 시작일</span>
          <input
            defaultValue={instantDate(filters.createdAtFrom)}
            key={filters.createdAtFrom}
            name="createdAtFrom"
            type="date"
          />
        </label>
        <label>
          <span>등록 종료일</span>
          <input
            defaultValue={instantDate(filters.createdAtTo)}
            key={filters.createdAtTo}
            name="createdAtTo"
            type="date"
          />
        </label>
        <label>
          <span>정렬</span>
          <select defaultValue={filters.sort} key={filters.sort} name="sort">
            <option value="createdAtDesc">최신 등록순</option>
            <option value="createdAtAsc">오래된 등록순</option>
          </select>
        </label>
        <label>
          <span>페이지 크기</span>
          <select
            defaultValue={String(filters.pageSize)}
            key={filters.pageSize}
            name="pageSize"
          >
            <option value="20">20개</option>
            <option value="50">50개</option>
            <option value="100">100개</option>
          </select>
        </label>
      </div>
      <div className={styles.filterActions}>
        <button onClick={onReset} type="button">
          초기화
        </button>
        <button className={styles.primaryAction} type="submit">
          필터 적용
        </button>
      </div>
      <p className={styles.filterNotice}>
        주제와 작성자는 현재 조건의 최근 게시물에서 선택할 수 있습니다.
      </p>
    </form>
  );
}
