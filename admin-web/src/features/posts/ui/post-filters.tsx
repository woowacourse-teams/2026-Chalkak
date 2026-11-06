"use client";

import { useState, type FormEvent } from "react";

import type { AdminPostFilters } from "../api/post-api";
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
  const [topicId, setTopicId] = useState(filters.topicId ?? "");
  const [syncedTopicId, setSyncedTopicId] = useState(filters.topicId);
  if (syncedTopicId !== filters.topicId) {
    setSyncedTopicId(filters.topicId);
    setTopicId(filters.topicId ?? "");
  }

  const [sort, setSort] = useState(filters.sort);
  const [syncedSort, setSyncedSort] = useState(filters.sort);
  if (syncedSort !== filters.sort) {
    setSyncedSort(filters.sort);
    setSort(filters.sort);
  }

  // 인기순은 주제가 있어야 하므로 주제를 비우면 최신순으로 되돌린다.
  const changeTopic = (value: string) => {
    setTopicId(value);
    if (!value) setSort((current) => (current === "likeCountDesc" ? "createdAtDesc" : current));
  };

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const value = (name: string) => String(data.get(name) ?? "").trim();

    const selectedTopicId = value("topicId") || undefined;

    onApply({
      status: filters.status,
      topicId: selectedTopicId,
      userId: value("userId") || undefined,
      instagramConsent: (value("instagramConsent") || undefined) as AdminPostFilters["instagramConsent"],
      instagramId: value("instagramId") || undefined,
      sort: value("sort") as AdminPostFilters["sort"],
      pageSize: Number(value("pageSize")),
      page: 1,
    });
  };

  return (
    <details className={styles.filters}>
      <summary>필터 {filters.topicId || filters.userId || filters.instagramConsent || filters.instagramId ? "· 적용 중" : ""}</summary>
    <form onSubmit={submit}>
      <div className={styles.filterGrid}>
        <label>
          <span>주제</span>
          <select
            key={`${filters.topicId}-${topicOptions.some((option) => option.value === filters.topicId)}`}
            name="topicId"
            onChange={(event) => changeTopic(event.target.value)}
            value={topicId}
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
          <span>인스타 동의</span>
          <select
            defaultValue={filters.instagramConsent ?? ""}
            key={filters.instagramConsent}
            name="instagramConsent"
          >
            <option value="">전체</option>
            <option value="agreed">동의함</option>
            <option value="notAgreed">동의 안 함</option>
            <option value="revoked">철회함</option>
          </select>
        </label>
        <label>
          <span>인스타그램 ID</span>
          <input
            defaultValue={filters.instagramId ?? ""}
            key={filters.instagramId}
            name="instagramId"
            placeholder="@ 없이 일부만 입력"
            type="text"
          />
        </label>
        <label>
          <span>정렬</span>
          <select
            name="sort"
            onChange={(event) => setSort(event.target.value as AdminPostFilters["sort"])}
            value={sort}
          >
            <option value="createdAtDesc">최신 등록순</option>
            <option value="createdAtAsc">오래된 등록순</option>
            <option disabled={!topicId} value="likeCountDesc">인기순 (좋아요 많은 순)</option>
          </select>
          {topicId ? null : <small className={styles.fieldHint}>인기순은 주제를 선택해야 사용할 수 있습니다.</small>}
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
      {filters.sort === "likeCountDesc" ? (
        <p className={styles.filterNotice}>
          인기순은 요청마다 다시 계산되어 페이지를 넘기는 사이 순위가 바뀔 수 있습니다. 새로고침하면 1페이지부터 다시 시작합니다.
        </p>
      ) : null}
      <p className={styles.filterNotice}>
        최근 게시물의 주제·작성자를 표시합니다. 다른 대상은 사용자·주제 목록의 게시물 건수로 이동할 수 있습니다.
      </p>
    </form>
    </details>
  );
}
