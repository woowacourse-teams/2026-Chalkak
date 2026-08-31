"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useRef, useState } from "react";

import { ConfirmDialog } from "@/shared/ui/confirm-dialog";
import { getSafeReturnTo } from "@/features/auth/model/return-to";
import {
  ErrorState,
  LoadingSkeleton,
} from "@/shared/ui/feedback-states";
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
  const safe = getSafeReturnTo(value);
  return /^\/(?:posts|audit-logs)(?:\?|$)/.test(safe) ? safe : "/posts?status=PENDING";
}

export function PostDetailScreen({ postId }: { postId: string }) {
  const searchParams = useSearchParams();
  const router = useRouter();
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
      <div className={styles.detailPage}>
      <Link className={styles.backLink} href={returnTo}>← 이전 목록으로 돌아가기</Link>
      <ErrorState
        description={getPostErrorMessage(postQuery.error)}
        onRetry={() => postQuery.refetch()}
        title={
          (postQuery.error as { status?: number })?.status === 404
            ? "게시물을 찾을 수 없습니다"
            : "게시물 상세를 불러오지 못했습니다"
        }
      />
      </div>
    );
  }

  const post = postQuery.data;

  const status = getPostDisplayStatus(post);
  const canModerate =
    post.moderationStatus === "PENDING" && post.deletedAt === null;
  const canDelete = post.deletedAt === null;

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
      router.push(returnTo);
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
          ← 이전 목록으로 돌아가기
        </Link>
      </div>

      <div className={styles.detailGrid}>
        <PostThumbnail
          alt={(post.title ?? "제목 없는 게시물") + " 원본 이미지"}
          detail
          src={post.photo?.originalImageUrl}
        />
        <section className={styles.detailPanel}>
          <div className={styles.detailTitleRow}>
            <h2>{post.title ?? "제목 없음"}</h2>
            <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
          </div>

          <dl className={styles.detailMeta}>
            <div>
              <dt>주제</dt>
              <dd>
                {post.topic ? <Link className={styles.relatedLink} href={"/topics/" + post.topic.topicId + "?" + new URLSearchParams({ returnTo })}>{post.topic.title} · {post.topic.topicDate}</Link> : "없음"}
              </dd>
            </div>
            <div>
              <dt>작성자</dt>
              <dd>{post.author ? <Link className={styles.relatedLink} href={"/users/" + post.author.userId + "?" + new URLSearchParams({ returnTo })}>{post.author.email ?? "이메일 정보 없음"}</Link> : "탈퇴/정보 없음"}</dd>
            </div>
            <div>
              <dt>등록 시각</dt>
              <dd>{formatInstant(post.createdAt)}</dd>
            </div>
            <div>
              <dt>처리 시각</dt>
              <dd>{formatInstant(post.moderatedAt)}</dd>
            </div>
          </dl>
          <details className={styles.extraInfo}>
            <summary>추가 정보</summary>
            <dl className={styles.detailMeta}>
              <div><dt>게시물 ID</dt><dd>{post.postId}</dd></div>
              <div><dt>사진 크기</dt><dd>{post.photo?.metadata.width ?? "—"} × {post.photo?.metadata.height ?? "—"} px</dd></div>
              <div><dt>파일 크기</dt><dd>{formatFileSize(post.photo?.metadata.byteSize)}</dd></div>
              <div><dt>좋아요</dt><dd>{post.likeCount.toLocaleString("ko-KR")}개</dd></div>
            </dl>
          </details>

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

          {canDelete ? <div aria-label="게시물 작업" className={styles.detailActions} role="group">
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
          </div> : null}
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
