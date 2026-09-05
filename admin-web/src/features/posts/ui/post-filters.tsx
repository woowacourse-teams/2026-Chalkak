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
      status: filters.status,
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
    <details className={styles.filters}>
      <summary>필터 {filters.topicId || filters.userId || filters.topicDate || filters.createdAtFrom || filters.createdAtTo ? "· 적용 중" : ""}</summary>
    <form onSubmit={submit}>
      <div className={styles.filterGrid}>
        <label>
          <span>주제</span>
          <select
            defaultValue={filters.topicId ?? ""}
            key={`${filters.topicId}-${topicOptions.some((option) => option.value === filters.topicId)}`}
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
            key={`${filters.userId}-${authorOptions.some((option) => option.value === filters.userId)}`}
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
          <span>등록 시작일 (한국 시간)</span>
          <input
            defaultValue={instantDate(filters.createdAtFrom)}
            key={filters.createdAtFrom}
            name="createdAtFrom"
            type="date"
          />
        </label>
        <label>
          <span>등록 종료일 (한국 시간)</span>
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
        최근 게시물의 주제·작성자를 표시합니다. 다른 대상은 사용자·주제 목록의 게시물 건수로 이동할 수 있습니다.
      </p>
    </form>
    </details>
  );
}
