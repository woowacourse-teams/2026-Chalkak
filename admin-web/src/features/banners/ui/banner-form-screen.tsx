"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef, useState, type FormEvent } from "react";
import { ApiError } from "@/shared/api/errors";
import { ConfirmDialog } from "@/shared/ui/confirm-dialog";
import { useToast } from "@/shared/ui/toast";
import { useBannerMutations } from "../api/banner-hooks";
import { normalizeBanner, validateBanner, type BannerInput } from "../model/banner";
import { BannerThumbnail } from "./banner-thumbnail";
import common from "@/shared/ui/management.module.css";
import styles from "./banners.module.css";

const fields = [["title", "제목", 50, "예: 이번 주, 일상을 담아보세요"], ["subtitle", "부제목", 100, "배너를 소개할 짧은 문구를 입력해 주세요"], ["webViewUrl", "웹뷰 URL", 2000, "https://your-page.vercel.app/event"], ["thumbnailUrl", "썸네일 URL", 2000, "https://example.com/banner.jpg"]] as const;
export function BannerFormScreen() {
  const router = useRouter();
  const { create } = useBannerMutations();
  const { showToast } = useToast();
  const [input, setInput] = useState<BannerInput>({ title: "", subtitle: "", webViewUrl: "", thumbnailUrl: "" });
  const [errors, setErrors] = useState<Partial<Record<keyof BannerInput, string>>>({});
  const [failure, setFailure] = useState<string | null>(null);
  const [confirm, setConfirm] = useState(false);
  const [previewUrl, setPreviewUrl] = useState("");
  const lock = useRef(false);
  function submit(event: FormEvent) {
    event.preventDefault();
    if (lock.current) return;
    const nextErrors = validateBanner(input);
    setErrors(nextErrors); setFailure(null);
    const firstError = Object.keys(nextErrors)[0];
    if (firstError) { document.getElementById(`banner-${firstError}`)?.focus(); return; }
    setConfirm(true);
  }
  async function save() {
    if (lock.current) return;
    lock.current = true;
    try {
      await create.mutateAsync(normalizeBanner(input));
      showToast("배너를 등록했습니다.", "success"); router.push("/banners");
    } catch (cause) {
      lock.current = false;
      setConfirm(false);
      setFailure(cause instanceof ApiError ? cause.message : "등록 결과를 확인하지 못했습니다.");
    }
  }
  return <div className={common.page}>
    <Link href="/banners" className={common.back}>← 배너 목록으로 돌아가기</Link>
    <h2 className={common.pageTitle}>새 배너 등록</h2>
    <div className={styles.intro}><p>웹페이지와 썸네일을 먼저 준비해 주세요.</p><span>네 항목을 모두 입력하면 앱에 바로 공개됩니다. URL은 HTTPS 주소를 사용해 주세요.</span></div>
    <div className={styles.formLayout}>
      <form className={common.form} onSubmit={submit} noValidate>
        <fieldset className={styles.fields} disabled={create.isPending}>
          <legend className={styles.srOnly}>배너 정보</legend>
          {fields.map(([key, label, max, placeholder]) => <label key={key} htmlFor={`banner-${key}`}>
            <span>{label} <span aria-hidden="true">*</span></span>
            <input id={`banner-${key}`} aria-label={label} value={input[key]} placeholder={placeholder} required onBlur={() => { if (key === "thumbnailUrl") setPreviewUrl(normalizeBanner(input).thumbnailUrl); }} aria-invalid={Boolean(errors[key])} aria-describedby={`banner-${key}-help`} inputMode={key.endsWith("Url") ? "url" : "text"} onChange={(event) => { setInput({ ...input, [key]: event.target.value }); setErrors({ ...errors, [key]: undefined }); }} />
            <span id={`banner-${key}-help`} className={errors[key] ? common.formError : styles.helper}>{errors[key] ?? `${Array.from(normalizeBanner(input)[key]).length.toLocaleString()} / ${max.toLocaleString()}자${key.endsWith("Url") ? " · HTTPS 절대 주소" : ""}`}</span>
          </label>)}
        </fieldset>
        {failure ? <div role="alert" className={styles.failure}><p>{failure}</p><span>응답을 받지 못했더라도 등록됐을 수 있습니다. 다시 등록하기 전에 <Link href="/banners">배너 목록을 확인해 주세요.</Link></span></div> : null}
        <div className={common.formActions}><Link className={common.secondaryButton} href="/banners">취소</Link><button className={common.primaryButton} type="submit" disabled={create.isPending}>{create.isPending ? "등록 중…" : "배너 등록"}</button></div>
      </form>
      <aside className={styles.preview} aria-label="배너 미리보기"><h3>미리보기</h3><BannerThumbnail key={previewUrl} url={previewUrl} title={input.title} /><strong>{input.title || "배너 제목"}</strong><p>{input.subtitle || "배너 부제목이 여기에 표시됩니다."}</p><span>실제 앱에서는 화면 크기에 따라 다르게 보일 수 있습니다.</span></aside>
    </div>
    <ConfirmDialog open={confirm} title="배너를 등록할까요?" description={`“${normalizeBanner(input).title}” 배너가 등록 즉시 앱에 공개됩니다.`} confirmLabel="등록하기" pending={create.isPending} onCancel={() => setConfirm(false)} onConfirm={() => { void save(); }} />
  </div>;
}
