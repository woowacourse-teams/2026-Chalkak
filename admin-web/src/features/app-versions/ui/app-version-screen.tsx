"use client";

import Link from "next/link";
import { useQueryClient } from "@tanstack/react-query";
import { useRef, useState, type FormEvent } from "react";
import { formatInstant } from "@/features/posts/model/post-display";
import type { AdminAppVersion, AdminAppVersionInput } from "@/shared/api/contracts";
import { ApiError } from "@/shared/api/errors";
import { queryKeys } from "@/shared/query/query-client";
import { ConfirmDialog } from "@/shared/ui/confirm-dialog";
import { ErrorState, LoadingSkeleton } from "@/shared/ui/feedback-states";
import { useToast } from "@/shared/ui/toast";
import { fetchAppVersions } from "../api/app-version-api";
import { useAppVersions, useSaveAppVersion } from "../api/app-version-hooks";
import { isStoreUrl, isVersionIncrease, normalizeAppVersion, platformLabels, sameAppVersion, stripInput, validateAppVersion } from "../model/app-version-form";
import common from "@/shared/ui/management.module.css";
import styles from "./app-versions.module.css";

function StoreLink({ value, setting }: { value: string | null; setting: AdminAppVersion }) {
  if (!value || !isStoreUrl(setting.platform, value)) return null;
  return <a className={styles.storeLink} href={stripInput(value)} target="_blank" rel="noopener noreferrer">스토어에서 확인 ↗<span className={styles.srOnly}> (새 창)</span></a>;
}

function SettingSummary({ setting }: { setting: AdminAppVersion }) {
  return <dl className={styles.summary}>
    <div><dt>최소 지원 버전</dt><dd>{setting.minimumSupportedVersion ?? "업데이트 제한 없음"}</dd></div>
    <div><dt>스토어 주소</dt><dd>{setting.storeUrl ?? "아직 설정하지 않았습니다"}</dd></div>
  </dl>;
}

function VersionEditor({ initial, onClose }: { initial: AdminAppVersion; onClose: () => void }) {
  // Capture the revision when editing begins, even if the query cache refreshes later.
  const [baseline, setBaseline] = useState(initial);
  const [version, setVersion] = useState(initial.minimumSupportedVersion ?? "");
  const [url, setUrl] = useState(initial.storeUrl ?? "");
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [recovery, setRecovery] = useState<"conflict" | "uncertain" | null>(null);
  const [latest, setLatest] = useState<AdminAppVersion | null>(null);
  const [checking, setChecking] = useState(false);
  const requestInFlight = useRef(false);
  const mutation = useSaveAppVersion(initial.platform);
  const client = useQueryClient();
  const { showToast } = useToast();
  const label = platformLabels[initial.platform];
  const input: AdminAppVersionInput = normalizeAppVersion({ revision: baseline.revision, minimumSupportedVersion: version, storeUrl: url, reason });
  const changed = !sameAppVersion(baseline, input);
  const busy = mutation.isPending || checking;
  const count = [...stripInput(reason)].length;

  function review(event: FormEvent) {
    event.preventDefault();
    if (busy || recovery || !changed) return;
    const validation = validateAppVersion(initial.platform, input);
    setError(validation);
    if (!validation) setConfirming(true);
  }

  async function save() {
    if (requestInFlight.current || recovery || !changed) return;
    const validation = validateAppVersion(initial.platform, input);
    if (validation) { setError(validation); setConfirming(false); return; }
    requestInFlight.current = true;
    setError(null);
    try {
      await mutation.mutateAsync(input);
      showToast(`${label} 설정을 저장했습니다.`, "success");
      onClose();
    } catch (caught) {
      setConfirming(false);
      if (caught instanceof ApiError && caught.errorCode === "RESOURCE_STATE_CHANGED") {
        setRecovery("conflict");
        setError("다른 관리자가 설정을 변경했습니다. 작성 내용은 유지했습니다. 최신 설정을 확인한 뒤 다시 저장해 주세요.");
      } else if (!(caught instanceof ApiError) || caught.kind !== "api" || (caught.status ?? 0) >= 500) {
        setRecovery("uncertain");
        setError("저장 결과를 확인하지 못했습니다. 이미 반영됐을 수 있으므로 현재 설정을 먼저 조회해 주세요.");
      } else setError(caught.message);
    } finally { requestInFlight.current = false; }
  }

  async function checkCurrent() {
    if (requestInFlight.current) return;
    requestInFlight.current = true;
    setChecking(true);
    setLatest(null);
    try {
      const result = await fetchAppVersions();
      setLatest(result.appVersions.find((row) => row.platform === initial.platform)!);
      client.setQueryData(queryKeys.appVersions, result);
      void client.invalidateQueries({ queryKey: queryKeys.auditLogs });
    } catch {
      setError("현재 설정을 조회하지 못했습니다. 작성 내용은 유지했습니다. 현재 설정 확인을 다시 시도해 주세요.");
    } finally { setChecking(false); requestInFlight.current = false; }
  }

  function continueEditing() {
    if (!latest) return;
    setBaseline(latest);
    setLatest(null);
    setRecovery(null);
    setError(null);
  }

  const increasing = isVersionIncrease(baseline.minimumSupportedVersion, version);
  const description = `${label} 설정을 저장합니다. 최소 지원 버전보다 낮은 앱은 업데이트가 필요합니다.\n\n최소 지원 버전: ${baseline.minimumSupportedVersion ?? "제한 없음"} → ${version}\n스토어 주소: ${baseline.storeUrl ?? "미설정"} → ${input.storeUrl}\n변경 사유: ${input.reason}${increasing ? "\n\n최소 버전을 올리기 전에 해당 앱과 버전이 대상 국가·기기에서 실제 설치 가능한지 확인해 주세요." : ""}`;

  return <form aria-label={`${label} 설정 편집`} className={styles.editor} noValidate onSubmit={review}>
    <fieldset disabled={busy || Boolean(recovery) || confirming} className={styles.fields}>
      <label><span>최소 지원 버전</span><input autoFocus value={version} onChange={(event) => setVersion(event.target.value)} placeholder="예: 1.10.0" autoComplete="off" required /></label>
      <p className={styles.help}>세 자리 숫자로 입력합니다. 잘못 적용한 제한은 버전을 낮춰 복구할 수 있습니다.</p>
      <label><span>스토어 주소</span><input value={url} onChange={(event) => setUrl(event.target.value)} placeholder={initial.platform === "ANDROID" ? "https://play.google.com/store/apps/details?id=…" : "https://apps.apple.com/app/id…"} autoComplete="off" type="url" required /></label>
      <StoreLink value={url} setting={initial} />
      <label><span>변경 사유</span><textarea value={reason} onChange={(event) => setReason(event.target.value)} rows={3} required aria-describedby={`${initial.platform}-reason-count`} placeholder="버전 또는 주소를 변경하는 이유를 입력해 주세요" /></label>
      <p className={styles.help} data-invalid={count > 500} id={`${initial.platform}-reason-count`}>{count} / 500자 · 앞뒤 공백 제외</p>
    </fieldset>
    {error ? <p className={styles.error} role="alert">{error}</p> : null}
    {recovery ? <section className={styles.recovery} aria-label="저장 상태 확인">
      <button className={common.secondaryButton} disabled={busy} onClick={() => { void checkCurrent(); }} type="button">{checking ? "확인 중…" : "현재 설정 확인"}</button>
      {latest ? <>
        <h4>최신 서버 설정</h4><SettingSummary setting={latest} />
        <p className={styles.help}>최종 변경: {latest.updatedAt ? formatInstant(latest.updatedAt) : "미설정"}</p>
        {sameAppVersion(latest, input) ? <>
          <p>현재 서버 설정이 입력한 버전·주소와 같습니다. 추가 저장 없이 사용할 수 있습니다.</p>
          <button className={common.primaryButton} onClick={onClose} type="button">저장된 설정 사용</button>
        </> : <>
          <p>작성 내용은 유지됩니다. 최신 설정을 기준으로 변경 내용을 다시 확인해 주세요.</p>
          <button className={common.secondaryButton} onClick={continueEditing} type="button">최신 설정 기준으로 계속 작성</button>
        </>}
      </> : null}
    </section> : null}
    <div className={styles.actions}>
      <button className={common.secondaryButton} disabled={busy} onClick={onClose} type="button">취소</button>
      <button className={common.primaryButton} disabled={busy || Boolean(recovery) || !changed} type="submit">{mutation.isPending ? "저장 중…" : "저장 내용 확인"}</button>
    </div>
    <ConfirmDialog open={confirming} title={`${label} 앱 버전 설정 저장`} description={description} confirmLabel="설정 저장" pending={mutation.isPending} onCancel={() => setConfirming(false)} onConfirm={() => { void save(); }} />
  </form>;
}

function PlatformCard({ setting }: { setting: AdminAppVersion }) {
  const [editing, setEditing] = useState(false);
  const label = platformLabels[setting.platform];
  const configured = setting.minimumSupportedVersion !== null;
  return <section className={styles.card} aria-label={`${label} 앱 버전`}>
    <header className={styles.cardHeader}>
      <div><h3>{label}</h3><span className={styles.badge} data-configured={configured}>{configured ? "최소 버전 적용 중" : "미설정"}</span></div>
      {!editing ? <button className={common.secondaryButton} onClick={() => setEditing(true)} type="button">{configured ? "수정" : "설정 입력"}</button> : null}
    </header>
    {editing ? <VersionEditor initial={setting} onClose={() => setEditing(false)} /> : <>
      <SettingSummary setting={setting} />
      {!configured ? <p className={styles.help}>관리자가 처음 저장하기 전에는 업데이트 제한 없이 이용할 수 있습니다.</p> : null}
      <StoreLink value={setting.storeUrl} setting={setting} />
    </>}
    {configured ? <dl className={styles.metadata}>
      <div><dt>최종 변경자 ID</dt><dd>{setting.lastChangedBy ?? "기록 없음"}</dd></div>
      <div><dt>변경 시각</dt><dd>{setting.updatedAt ? <time dateTime={setting.updatedAt}>{formatInstant(setting.updatedAt)}</time> : "기록 없음"}</dd></div>
      <div><dt>이전 변경 사유</dt><dd>{setting.changeReason ?? "기록 없음"}</dd></div>
    </dl> : null}
  </section>;
}

export function AppVersionScreen() {
  const query = useAppVersions();
  return <div className={common.page}>
    <header className={styles.heading}><div><h2 className={common.pageTitle}>앱 버전 관리</h2><p>플랫폼별 최소 지원 버전과 업데이트할 스토어를 설정합니다.</p></div><Link href="/audit-logs?targetType=APP_VERSION" className={common.secondaryButton}>변경 이력 →</Link></header>
    <aside className={styles.notice}><strong>최소 지원 버전을 올리기 전에 확인해 주세요</strong><p>실제 Chalkak 앱의 주소인지, 해당 버전 이상을 대상 국가·기기에서 설치할 수 있는지 스토어에서 확인해 주세요. 단계적 출시 중이라면 모든 대상 사용자에게 제공되는지도 확인해야 합니다.</p><p>저장한 설정은 다음 앱 버전 조회부터 반영됩니다. 이 설정을 조회하는 기능이 연동된 앱에 업데이트 안내가 적용됩니다.</p></aside>
    {query.isPending ? <LoadingSkeleton rows={6} /> : null}
    {query.isError ? <ErrorState description={query.error instanceof ApiError ? query.error.message : "앱 버전 설정을 불러오지 못했습니다."} onRetry={() => { void query.refetch(); }} /> : null}
    {query.data ? <div className={styles.grid}>{query.data.appVersions.map((setting) => <PlatformCard setting={setting} key={setting.platform} />)}</div> : null}
  </div>;
}
