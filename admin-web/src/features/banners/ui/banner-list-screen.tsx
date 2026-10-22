"use client";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useRef, useState } from "react";
import { formatInstant } from "@/features/posts/model/post-display";
import { ApiError } from "@/shared/api/errors";
import { ConfirmDialog } from "@/shared/ui/confirm-dialog";
import { EmptyState, ErrorState, LoadingSkeleton } from "@/shared/ui/feedback-states";
import { Pagination } from "@/shared/ui/pagination";
import { useToast } from "@/shared/ui/toast";
import { useBannerMutations, useBanners } from "../api/banner-hooks";
import { isHttpsUrl, type Banner } from "../model/banner";
import { BannerThumbnail } from "./banner-thumbnail";
import common from "@/shared/ui/management.module.css";
import styles from "./banners.module.css";

export function BannerListScreen() {
  const router = useRouter();
  const params = useSearchParams();
  const rawPage = Number(params.get("page") ?? 1);
  const page = Number.isSafeInteger(rawPage) && rawPage > 0 && rawPage <= 107374183 ? rawPage : 1;
  const query = useBanners(page);
  const { remove } = useBannerMutations();
  const { showToast } = useToast();
  const [selected, setSelected] = useState<Banner | null>(null);
  const [error, setError] = useState<string | null>(null);
  const lock = useRef(false);
  async function confirmDelete() {
    if (!selected || lock.current) return;
    lock.current = true;
    try {
      await remove.mutateAsync(selected.id);
      setSelected(null);
      showToast("배너를 삭제했습니다.", "success");
      if (query.data?.banners.length === 1 && page > 1) router.push(`/banners?page=${page - 1}`);
    } catch (cause) { setError(cause instanceof ApiError ? cause.message : "배너를 삭제하지 못했습니다. 목록을 확인한 뒤 다시 시도해 주세요."); }
    finally { lock.current = false; }
  }
  return <div className={common.page}>
    <div className={common.toolbar}><h2 className={common.pageTitle}>배너 관리</h2><Link className={common.primaryButton} href="/banners/new">배너 등록</Link></div>
    <div className={styles.intro}><p>앱에서 웹뷰로 연결할 배너를 관리합니다.</p><span>등록한 배너는 바로 공개됩니다. 변경하려면 기존 배너를 삭제한 후 새로 등록해 주세요.</span></div>
    <div className={styles.heading}><span>등록 최신순 · 미삭제 배너만 표시</span><Link href="/audit-logs?targetType=BANNER">배너 처리 이력 →</Link></div>
    {query.isPending ? <LoadingSkeleton rows={3} /> : null}
    {query.isError ? <ErrorState description={query.error instanceof ApiError ? query.error.message : "배너를 불러오지 못했습니다."} onRetry={() => { void query.refetch(); }} /> : null}
    {!query.isError && query.data?.banners.length === 0 ? <EmptyState title="표시할 배너가 없습니다" description="새 배너를 등록하거나 이전 페이지를 확인해 주세요." /> : null}
    {!query.isError && query.data?.banners.length ? <ul className={styles.list} aria-label="배너 목록">{query.data.banners.map((banner) => <li className={styles.card} key={banner.id}>
      <BannerThumbnail url={banner.thumbnailUrl} title={banner.title} />
      <div className={styles.content}><h3>{banner.title}</h3><p>{banner.subtitle}</p>
        <time dateTime={banner.createdAt}>등록 {formatInstant(banner.createdAt)} KST</time>
        {isHttpsUrl(banner.webViewUrl) ? <a href={banner.webViewUrl} target="_blank" rel="noopener noreferrer" className={styles.webLink}>웹뷰 열기 ↗<span>{banner.webViewUrl}</span></a> : <span>웹뷰 주소를 확인해 주세요.</span>}
      </div>
      <button className={common.dangerButton} type="button" disabled={remove.isPending} aria-label={`${banner.title} 삭제`} onClick={() => { setError(null); setSelected(banner); }}>삭제</button>
    </li>)}</ul> : null}
    {!query.isError && query.data ? <Pagination currentPage={query.data.currentPage} hasNext={query.data.hasNext} onPageChange={(next) => router.push(`/banners?page=${next}`)} /> : null}
    <ConfirmDialog open={selected !== null} title="배너를 삭제할까요?" description={`“${selected?.title ?? ""}” 배너가 앱 목록에서 사라집니다. 연결된 웹페이지와 이미지 파일은 삭제되지 않습니다.`} confirmLabel="배너 삭제" destructive pending={remove.isPending} error={error} onCancel={() => setSelected(null)} onConfirm={() => { void confirmDelete(); }} />
  </div>;
}
