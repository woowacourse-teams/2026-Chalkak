"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useMemo } from "react";

import type { AdminPostListItem } from "@/shared/api/contracts";
import { EmptyState, ErrorState, LoadingSkeleton } from "@/shared/ui/feedback-states";
import { Pagination } from "@/shared/ui/pagination";
import { StatusBadge } from "@/shared/ui/status-badge";
import { Table, type TableColumn } from "@/shared/ui/table";

import { useAdminPosts } from "../api/post-hooks";
import {
  readAdminPostFilters,
  withQueryPatch,
} from "../model/post-filter-query";
import {
  formatInstant,
  getPostDisplayStatus,
  getPostErrorMessage,
} from "../model/post-display";
import { PostFilters, type FilterOption } from "./post-filters";
import { PostThumbnail } from "./post-thumbnail";
import styles from "./posts.module.css";

function detailHref(postId: string, returnTo: string) {
  const query = new URLSearchParams({ returnTo });
  return "/posts/" + postId + "?" + query.toString();
}

export function PostListScreen() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const serializedSearch = searchParams.toString();
  const filters = useMemo(
    () => readAdminPostFilters(new URLSearchParams(serializedSearch)),
    [serializedSearch],
  );
  const postsQuery = useAdminPosts(filters);
  const filterOptionFilters = useMemo(
    () => ({
      ...filters,
      topicId: undefined,
      userId: undefined,
      page: 1,
      pageSize: 100,
    }),
    [filters],
  );
  const filterOptionsQuery = useAdminPosts(filterOptionFilters);
  const returnTo = pathname + (serializedSearch ? "?" + serializedSearch : "");

  const topicOptions = useMemo<FilterOption[]>(() => {
    const options = new Map<string, string>();
    filterOptionsQuery.data?.posts.forEach((post) => {
      if (post.topic) {
        options.set(
          post.topic.topicId,
          post.topic.title + " · " + post.topic.topicDate,
        );
      }
    });
    return Array.from(options, ([value, label]) => ({ value, label }));
  }, [filterOptionsQuery.data?.posts]);

  const authorOptions = useMemo<FilterOption[]>(() => {
    const options = new Map<string, string>();
    filterOptionsQuery.data?.posts.forEach((post) => {
      if (post.author) {
        options.set(
          post.author.userId,
          post.author.email ?? "이메일 정보 없음",
        );
      }
    });
    return Array.from(options, ([value, label]) => ({ value, label }));
  }, [filterOptionsQuery.data?.posts]);

  const updateFilters = (patch: Record<string, string | number | undefined>) => {
    router.push(pathname + withQueryPatch(searchParams, patch));
  };

  const columns: TableColumn<AdminPostListItem>[] = [
    {
      id: "photo",
      header: "사진",
      render: (post) => (
        <PostThumbnail
          alt={(post.title ?? "제목 없는 게시물") + " 썸네일"}
          src={post.photo?.thumbnailImageUrl ?? post.photo?.originalImageUrl}
        />
      ),
    },
    {
      id: "post",
      header: "게시물",
      render: (post) => (
        <Link
          className={styles.postLink}
          href={detailHref(post.postId, returnTo)}
        >
          <strong>{post.title ?? "제목 없음"}</strong>
          <span>{post.postId}</span>
        </Link>
      ),
    },
    {
      id: "status",
      header: "상태",
      render: (post) => {
        const status = getPostDisplayStatus(post);
        return <StatusBadge tone={status.tone}>{status.label}</StatusBadge>;
      },
    },
    {
      id: "topic",
      header: "주제",
      render: (post) => (
        <span className={styles.secondaryText}>
          {post.topic?.title ?? "연결된 주제 없음"}
        </span>
      ),
    },
    {
      id: "author",
      header: "작성자",
      render: (post) => (
        <span className={styles.secondaryText}>
          {post.author?.email ?? "탈퇴/정보 없음"}
        </span>
      ),
    },
    {
      id: "createdAt",
      header: "등록 시각",
      align: "right",
      render: (post) => formatInstant(post.createdAt),
    },
  ];

  return (
    <div className={styles.postsPage}>
      <section className={styles.listIntro}>
        <div>
          <p>POST MODERATION</p>
          <h2>검수가 필요한 순간을 놓치지 마세요.</h2>
          <span>
            상태와 작성자, 주제, 날짜를 조합해 게시물을 찾고 상세에서
            안전하게 처리합니다.
          </span>
        </div>
        <strong>{postsQuery.data?.posts.length ?? 0}</strong>
      </section>

      <PostFilters
        authorOptions={authorOptions}
        filters={filters}
        onApply={(next) =>
          updateFilters({
            status: next.status,
            topicId: next.topicId,
            topicDate: next.topicDate,
            userId: next.userId,
            createdAtFrom: next.createdAtFrom,
            createdAtTo: next.createdAtTo,
            sort: next.sort,
            pageSize: next.pageSize,
            page: 1,
          })
        }
        onReset={() => router.push(pathname)}
        topicOptions={topicOptions}
      />

      {postsQuery.isPending ? <LoadingSkeleton rows={5} /> : null}
      {postsQuery.isError ? (
        <ErrorState
          description={getPostErrorMessage(postsQuery.error)}
          onRetry={() => postsQuery.refetch()}
        />
      ) : null}
      {postsQuery.data?.posts.length === 0 ? (
        <EmptyState
          description="필터 조건을 변경하거나 새 게시물이 등록될 때까지 기다려 주세요."
          title="조건에 맞는 게시물이 없습니다"
        />
      ) : null}
      {postsQuery.data?.posts.length ? (
        <>
          <div className={styles.desktopList}>
            <Table
              caption="관리자 게시물 검수 목록"
              columns={columns}
              getRowKey={(post) => post.postId}
              rows={postsQuery.data.posts}
            />
          </div>
          <div className={styles.mobileList}>
            {postsQuery.data.posts.map((post) => {
              const status = getPostDisplayStatus(post);
              return (
                <Link
                  className={styles.mobilePostCard}
                  href={detailHref(post.postId, returnTo)}
                  key={post.postId}
                >
                  <PostThumbnail
                    alt={(post.title ?? "제목 없는 게시물") + " 썸네일"}
                    src={
                      post.photo?.thumbnailImageUrl ??
                      post.photo?.originalImageUrl
                    }
                  />
                  <div>
                    <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
                    <strong>{post.title ?? "제목 없음"}</strong>
                    <span>{post.topic?.title ?? "연결된 주제 없음"}</span>
                    <small>{formatInstant(post.createdAt)}</small>
                  </div>
                </Link>
              );
            })}
          </div>
          <Pagination
            currentPage={postsQuery.data.currentPage}
            hasNext={postsQuery.data.hasNext}
            onPageChange={(page) => updateFilters({ page })}
          />
        </>
      ) : null}
    </div>
  );
}
