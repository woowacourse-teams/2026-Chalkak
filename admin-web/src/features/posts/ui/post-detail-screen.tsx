"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useRef, useState } from "react";

import { ConfirmDialog } from "@/shared/ui/confirm-dialog";
import { ErrorState, LoadingSkeleton } from "@/shared/ui/feedback-states";
import { StatusBadge } from "@/shared/ui/status-badge";
import { useToast } from "@/shared/ui/toast";

import {
  useAdminPost,
  useDeleteAdminPost,
  useModerateAdminPost,
} from "../api/post-hooks";
import {
  formatFileSize,
  formatInstant,
  getPostDisplayStatus,
  getPostErrorMessage,
} from "../model/post-display";
import { PostThumbnail } from "./post-thumbnail";
import styles from "./posts.module.css";

type PendingAction = "approve" | "reject" | "delete" | null;

function safeReturnTo(value: string | null) {
  return value?.startsWith("/posts") ? value : "/posts";
}

export function PostDetailScreen({ postId }: { postId: string }) {
  const searchParams = useSearchParams();
  const postQuery = useAdminPost(postId);
  const moderation = useModerateAdminPost();
  const deletion = useDeleteAdminPost();
  const { showToast } = useToast();
  const [action, setAction] = useState<PendingAction>(null);
  const requestLockRef = useRef(false);
  const isProcessing = moderation.isPending || deletion.isPending;
  const returnTo = safeReturnTo(searchParams.get("returnTo"));

  if (postQuery.isPending) {
    return <LoadingSkeleton rows={6} />;
  }

  if (postQuery.isError || !postQuery.data) {
    return (
      <ErrorState
        description={getPostErrorMessage(postQuery.error)}
        onRetry={() => postQuery.refetch()}
        title={
          (postQuery.error as { status?: number })?.status === 404
            ? "게시물을 찾을 수 없습니다"
            : "게시물 상세를 불러오지 못했습니다"
        }
      />
    );
  }

  const post = postQuery.data;
  const status = getPostDisplayStatus(post);
  const canModerate =
    post.moderationStatus === "PENDING" && post.deletedAt === null;
  const canDelete =
    post.moderationStatus !== "VALIDATING" && post.deletedAt === null;

  const confirmAction = async (reason: string) => {
    if (!action || isProcessing || requestLockRef.current) {
      return;
    }

    requestLockRef.current = true;
    try {
      if (action === "approve") {
        await moderation.mutateAsync({ postId, status: "APPROVED" });
        showToast("게시물을 승인했습니다.", "success");
      } else if (action === "reject") {
        await moderation.mutateAsync({
          postId,
          status: "REJECTED",
          reason,
        });
        showToast("게시물을 거절했습니다.", "success");
      } else {
        await deletion.mutateAsync({ postId, reason });
        showToast("게시물을 삭제했습니다.", "success");
      }
      setAction(null);
    } catch (error) {
      showToast(getPostErrorMessage(error), "error");
      await postQuery.refetch();
    } finally {
      requestLockRef.current = false;
    }
  };

  const dialog =
    action === "approve"
      ? {
          title: "이 게시물을 승인할까요?",
          description: "승인하면 사용자 서비스에 게시물이 노출될 수 있습니다.",
          confirmLabel: "승인",
        }
      : action === "reject"
        ? {
            title: "이 게시물을 거절할까요?",
            description: "거절 사유는 처리 이력과 감사 로그에 기록됩니다.",
            confirmLabel: "거절",
            reasonField: {
              label: "거절 사유",
              placeholder: "거절 사유를 입력해 주세요.",
              required: true,
              maxLength: 500,
            },
          }
        : {
            title: "이 게시물을 삭제할까요?",
            description:
              "게시물은 사용자 화면에서 숨겨지며 원본과 썸네일은 관리자 확인을 위해 보관됩니다.",
            confirmLabel: "삭제",
            reasonField: {
              label: "삭제 사유",
              placeholder: "삭제 사유를 입력해 주세요.",
              required: true,
              maxLength: 500,
            },
          };

  return (
    <div className={styles.detailPage}>
      <div className={styles.detailTopBar}>
        <Link className={styles.backLink} href={returnTo}>
          ← 목록과 필터로 돌아가기
        </Link>
        <span className={styles.secondaryText}>{post.postId}</span>
      </div>

      <div className={styles.detailGrid}>
        <PostThumbnail
          alt={(post.title ?? "제목 없는 게시물") + " 원본 이미지"}
          detail
          src={post.photo?.originalImageUrl}
        />
        <section className={styles.detailPanel}>
          <p className={styles.detailEyebrow}>POST REVIEW</p>
          <div className={styles.detailTitleRow}>
            <h2>{post.title ?? "제목 없음"}</h2>
            <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
          </div>

          <dl className={styles.detailMeta}>
            <div>
              <dt>주제</dt>
              <dd>
                {post.topic?.title ?? "없음"}
                {post.topic ? " · " + post.topic.topicDate : ""}
              </dd>
            </div>
            <div>
              <dt>작성자</dt>
              <dd>{post.author?.email ?? "탈퇴/정보 없음"}</dd>
            </div>
            <div>
              <dt>등록 시각</dt>
              <dd>{formatInstant(post.createdAt)}</dd>
            </div>
            <div>
              <dt>처리 시각</dt>
              <dd>{formatInstant(post.moderatedAt)}</dd>
            </div>
            <div>
              <dt>사진 크기</dt>
              <dd>
                {post.photo?.metadata.width ?? "—"} ×{" "}
                {post.photo?.metadata.height ?? "—"} px
              </dd>
            </div>
            <div>
              <dt>파일 크기</dt>
              <dd>{formatFileSize(post.photo?.metadata.byteSize)}</dd>
            </div>
            <div>
              <dt>좋아요</dt>
              <dd>{post.likeCount.toLocaleString("ko-KR")}개</dd>
            </div>
            <div>
              <dt>이미지 처리</dt>
              <dd>{post.imageUpload?.status ?? "정보 없음"}</dd>
            </div>
          </dl>

          {post.rejectionReason ? (
            <p className={styles.moderationNote}>
              거절 사유 · {post.rejectionReason}
            </p>
          ) : null}
          {post.deletedAt ? (
            <p className={styles.moderationNote}>
              삭제 시각 · {formatInstant(post.deletedAt)}
            </p>
          ) : null}

          <div className={styles.detailActions}>
            {canModerate ? (
              <>
                <button
                  className={styles.approveButton}
                  disabled={isProcessing}
                  onClick={() => setAction("approve")}
                  type="button"
                >
                  승인
                </button>
                <button
                  className={styles.rejectButton}
                  disabled={isProcessing}
                  onClick={() => setAction("reject")}
                  type="button"
                >
                  거절
                </button>
              </>
            ) : null}
            {canDelete ? (
              <button
                className={styles.deleteButton}
                disabled={isProcessing}
                onClick={() => setAction("delete")}
                type="button"
              >
                삭제
              </button>
            ) : null}
          </div>
        </section>
      </div>

      <ConfirmDialog
        confirmLabel={dialog.confirmLabel}
        description={dialog.description}
        destructive={action === "reject" || action === "delete"}
        onCancel={() => setAction(null)}
        onConfirm={confirmAction}
        open={action !== null}
        pending={isProcessing}
        reasonField={dialog.reasonField}
        title={dialog.title}
      />
    </div>
  );
}
