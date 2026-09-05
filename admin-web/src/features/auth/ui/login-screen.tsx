"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState, type FormEvent, type PropsWithChildren } from "react";

import { useAdminSession } from "@/features/auth/admin-session-provider";
import { getSafeReturnTo } from "@/features/auth/model/return-to";
import { ApiError } from "@/shared/api/errors";
import { AdminIcon } from "@/shared/ui/admin-icon";

import styles from "./login-screen.module.css";

const logoutWarning = "로그아웃 확인에 실패했습니다. 연결 상태를 확인하고 다시 시도해 주세요.";

function LoginFrame({ children }: PropsWithChildren) {
  return (
    <main className={styles.page}>
      <div className={styles.panel}>
        <h1>Chalkak 관리자</h1>
        {children}
      </div>
    </main>
  );
}

export function LoginLoading() {
  return <LoginFrame><p className={styles.description} role="status">로그인 상태를 확인하고 있습니다.</p></LoginFrame>;
}

export function LoginScreen() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { status, error, isMock, login, logout } = useAdminSession();
  const safeReturnTo = getSafeReturnTo(searchParams.get("returnTo"));
  const logoutFailed = searchParams.get("logout") === "failed";
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [pendingAction, setPendingAction] = useState<"login" | "demo" | "logout" | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const errorRef = useRef<HTMLParagraphElement>(null);
  const redirectedTo = useRef<string | null>(null);
  const isPending = pendingAction !== null;
  const isCheckingSession = status === "loading" || status === "logging-out";
  const sessionError = status === "forbidden"
    ? error ?? "관리자 권한이 필요합니다."
    : status === "error" ? error ?? "로그인 상태를 확인하지 못했습니다." : null;
  const displayedError = formError ?? sessionError;

  const openWorkspace = useCallback(() => {
    if (logoutFailed || redirectedTo.current === safeReturnTo) return;
    redirectedTo.current = safeReturnTo;
    router.replace(safeReturnTo);
  }, [logoutFailed, router, safeReturnTo]);

  useEffect(() => {
    if (status === "authenticated" && !logoutFailed) openWorkspace();
  }, [logoutFailed, openWorkspace, status]);

  useEffect(() => {
    if (formError) errorRef.current?.focus();
  }, [formError]);

  async function submitLogin(loginUsername: string, loginPassword: string, action: "login" | "demo") {
    if (isPending || isCheckingSession) return;
    setFormError(null);
    setPendingAction(action);
    try {
      await login(loginUsername, loginPassword);
      setPassword("");
      openWorkspace();
    } catch (cause) {
      setFormError(cause instanceof ApiError ? cause.message : "로그인하지 못했습니다. 연결 상태를 확인하고 다시 시도해 주세요.");
    } finally {
      setPendingAction(null);
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!username.trim() || !password) {
      setFormError("아이디와 비밀번호를 입력해 주세요.");
      return;
    }
    void submitLogin(username.trim(), password, "login");
  }

  async function retryLogout() {
    if (isPending) return;
    setPendingAction("logout");
    try {
      await logout();
      router.replace("/login");
    } catch {
      setFormError(logoutWarning);
    } finally {
      setPendingAction(null);
    }
  }

  if (logoutFailed) {
    return (
      <LoginFrame>
        <p className={styles.error} ref={errorRef} role="alert" tabIndex={-1}>{logoutWarning}</p>
        <button className={styles.submitButton} disabled={isPending} onClick={retryLogout} type="button">
          {pendingAction === "logout" ? "로그아웃 확인 중…" : "로그아웃 다시 시도"}
        </button>
      </LoginFrame>
    );
  }

  if (status === "authenticated") {
    return <LoginFrame><p className={styles.description} role="status">관리자 화면으로 이동하고 있습니다.</p></LoginFrame>;
  }

  return (
    <LoginFrame>
      <p className={styles.description}>관리자 계정으로 로그인해 주세요.</p>
      {isCheckingSession ? <p className={styles.sessionStatus} role="status">로그인 상태를 확인하고 있습니다.</p> : null}
      <form aria-busy={isPending || isCheckingSession} aria-label="관리자 로그인" className={styles.form} onSubmit={handleSubmit}>
        <div className={styles.field}>
          <label htmlFor="admin-username">아이디</label>
          <input aria-describedby={displayedError ? "login-error" : undefined} autoCapitalize="none" autoComplete="username" disabled={isPending || isCheckingSession} id="admin-username" maxLength={100} name="username" onChange={(event) => setUsername(event.target.value)} required spellCheck={false} value={username} />
        </div>
        <div className={styles.field}>
          <label htmlFor="admin-password">비밀번호</label>
          <div className={styles.passwordField}>
            <input aria-describedby={displayedError ? "login-error" : undefined} autoComplete="current-password" disabled={isPending || isCheckingSession} id="admin-password" maxLength={200} name="password" onChange={(event) => setPassword(event.target.value)} required type={passwordVisible ? "text" : "password"} value={password} />
            <button aria-label={passwordVisible ? "비밀번호 숨기기" : "비밀번호 보기"} aria-pressed={passwordVisible} className={styles.revealButton} disabled={isPending || isCheckingSession} onClick={() => setPasswordVisible((visible) => !visible)} type="button">
              <AdminIcon name={passwordVisible ? "eye-off" : "eye"} />
            </button>
          </div>
        </div>
        {displayedError ? <p className={styles.error} id="login-error" ref={errorRef} role="alert" tabIndex={-1}>{displayedError}</p> : null}
        <button className={styles.submitButton} disabled={isPending || isCheckingSession} type="submit">
          {pendingAction === "login" ? "로그인 중…" : "로그인"}
        </button>
      </form>
      {isMock ? (
        <div className={styles.demo}>
          <p>데모 환경입니다. 실제 서비스에 영향을 주지 않습니다.</p>
          <button className={styles.demoButton} disabled={isPending || isCheckingSession} onClick={() => { void submitLogin("demo-admin", "demo-only", "demo"); }} type="button">
            {pendingAction === "demo" ? "데모 로그인 중…" : "데모 계정으로 둘러보기"}
          </button>
        </div>
      ) : null}
    </LoginFrame>
  );
}
