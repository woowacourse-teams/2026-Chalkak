"use client";

import { useEffect, useMemo, useRef, useState } from "react";

import { StoreBadges } from "@/shared/ui/store-badges";

import { cropBackground, frameRect, overlapRatio, type Rect } from "./camera";
import { onggiScene, onggiShots } from "./scene";
import { SHARING_ENABLED, type SharedShot } from "./shared-shot";
import styles from "./experience.module.css";

type Sort = "latest" | "popular" | "random";
/** mine: 방금 찍은 내 작품, visitor: 다른 방문자가 공유한 작품(공유를 켰을 때), example: 예시 작품(공유를 껐을 때) */
type Kind = "mine" | "visitor" | "example";
type Card = {
  id: string;
  line: string;
  /** 사진 위에 손글씨로 보이는 작가 사인. 익명 방문자 작품에는 없다. */
  sign?: string;
  likes: number;
  /** 최신순 정렬용. 몇 분 전에 전시됐는지 */
  minutesAgo: number;
  rect: Rect;
  kind: Kind;
  titled: boolean;
};
type Share = { status: "off" } | { status: "loading" } | { status: "shared"; visitors: SharedShot[]; receivedAt: number } | { status: "failed"; message: string };

const SIMILAR_OVERLAP = 0.2;
const MAP_STAGGER_MS = 70;
const sceneAspect = onggiScene.size.width / onggiScene.size.height;

const examples: Card[] = onggiShots.map((item) => ({
  id: item.id,
  line: item.line,
  sign: item.sign,
  likes: item.likes,
  minutesAgo: item.minutesAgo,
  rect: frameRect(item, sceneAspect),
  kind: "example",
  titled: true,
}));

function visitorCard(shot: SharedShot, now: number): Card {
  return {
    id: `visitor-${shot.id}`,
    line: shot.title ?? "제목 없음",
    likes: 0,
    minutesAgo: Math.max(0, (now - Date.parse(shot.createdAt)) / 60000),
    rect: shot.rect,
    kind: "visitor",
    titled: shot.title !== null,
  };
}

/** 공유를 켰다면 결과 화면에 들어올 때 내 작품을 한 번 공유하고, 다른 방문자의 최근 작품을 받는다. */
function useShare(shot: Rect, title: string | null): Share {
  const [share, setShare] = useState<Share>(SHARING_ENABLED ? { status: "loading" } : { status: "off" });
  const sent = useRef(false);

  useEffect(() => {
    // 개발 모드의 StrictMode가 effect를 두 번 실행해도 한 번만 보낸다.
    if (!SHARING_ENABLED || sent.current) return;
    sent.current = true;
    fetch("/api/shots", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ rect: shot, title }),
    })
      .then(async (response) => {
        const data = (await response.json().catch(() => ({}))) as { shots?: SharedShot[]; error?: string };
        if (response.ok && Array.isArray(data.shots)) setShare({ status: "shared", visitors: data.shots, receivedAt: Date.now() });
        else setShare({ status: "failed", message: data.error ?? "지금은 작품을 공유할 수 없어요." });
      })
      .catch(() => setShare({ status: "failed", message: "네트워크 연결을 확인해 주세요." }));
  }, [shot, title]);

  return share;
}

const sorts: { id: Sort; label: string }[] = [
  { id: "latest", label: "최신순" },
  { id: "popular", label: "인기순" },
  { id: "random", label: "랜덤순" },
];

function shuffled<T>(items: T[]) {
  const copy = [...items];
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}

export function Reveal({ shot, title, source, signature, onAgain }: { shot: Rect; title: string | null; source: string; signature: string; onAgain: () => void }) {
  const share = useShare(shot, title);
  // 공유를 끄면 다른 방문자 대신 예시 작품과 함께 보여 준다. 내 작품은 다른 사람에게 보이지 않는다.
  const others = useMemo<Card[]>(() => {
    if (share.status === "off") return examples;
    if (share.status !== "shared") return [];
    return share.visitors.map((item) => visitorCard(item, share.receivedAt));
  }, [share]);
  const cards: Card[] = [{ id: "mine", line: title ?? "나의 온기", likes: 0, minutesAgo: 0, rect: shot, kind: "mine", titled: true }, ...others];

  const similar = others.filter((item) => overlapRatio(shot, item.rect) > SIMILAR_OVERLAP).sort((a, b) => overlapRatio(shot, b.rect) - overlapRatio(shot, a.rect));
  const quoted = similar.find((item) => item.titled);
  const message = (() => {
    if (share.status === "off") {
      return similar.length > 0 ? `${similar.length}명이 당신과 비슷한 곳을 봤어요. 그중 한 명은 이렇게 불렀어요 — “${similar[0].line}”` : "아무도 보지 않은 곳을 찾았어요. 당신만의 온기네요.";
    }
    if (share.status === "loading") return "다른 방문자들의 시선을 불러오고 있어요…";
    if (share.status === "failed") return `${share.message} 다른 방문자의 작품은 잠시 후 다시 확인해 주세요.`;
    if (others.length === 0) return "아직 이 풍경을 찍은 다른 방문자가 없어요. 당신이 첫 번째 시선이에요.";
    if (similar.length === 0) return "아무도 보지 않은 곳을 찾았어요. 당신만의 온기네요.";
    return `${similar.length}개의 시선이 당신과 비슷한 곳을 봤어요.${quoted ? ` 그중 하나는 이렇게 불렀어요 — “${quoted.line}”` : ""}`;
  })();

  const [sort, setSort] = useState<Sort>("latest");
  const [randomOrder, setRandomOrder] = useState<string[]>([]);
  const [liked, setLiked] = useState<Set<string>>(() => new Set());

  const likesOf = (card: Card) => card.likes + (liked.has(card.id) ? 1 : 0);
  const ordered = (() => {
    if (sort === "latest") return [...cards].sort((a, b) => a.minutesAgo - b.minutesAgo);
    if (sort === "popular") return [...cards].sort((a, b) => likesOf(b) - likesOf(a));
    return randomOrder.map((id) => cards.find((card) => card.id === id)).filter((card): card is Card => Boolean(card));
  })();

  const chooseSort = (next: Sort) => {
    if (next === "random") setRandomOrder(shuffled(cards.map((card) => card.id)));
    setSort(next);
  };

  const toggleLike = (id: string) =>
    setLiked((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  return (
    <section className={styles.reveal} aria-labelledby="reveal-title">
      <p className={styles.eyebrow}>같은 풍경, 같은 주제</p>
      <h2 id="reveal-title" className={styles.revealTitle}>
        {share.status === "shared" || share.status === "off" ? <>&lsquo;{onggiScene.topic}&rsquo;를 찾은 {cards.length}개의 시선</> : <>&lsquo;{onggiScene.topic}&rsquo;를 찾은 시선들</>}
      </h2>
      <p className={styles.match} aria-live="polite">
        {message}
      </p>

      <div
        className={styles.map}
        style={{ aspectRatio: `${sceneAspect}`, backgroundImage: `url(${onggiScene.sources.preview})` }}
        role="img"
        aria-label={`풍경 위에 ${cards.length}개의 프레임을 겹친 시선 지도. 강조된 사각형이 내 시선이에요.`}
      >
        {[...others, cards[0]].map((card, index) => (
          <span
            key={card.id}
            className={card.kind === "mine" ? `${styles.frame} ${styles.frameMine}` : styles.frame}
            style={{
              left: `${card.rect.x * 100}%`,
              top: `${card.rect.y * 100}%`,
              width: `${card.rect.width * 100}%`,
              height: `${card.rect.height * 100}%`,
              animationDelay: `${index * MAP_STAGGER_MS}ms`,
            }}
          />
        ))}
      </div>

      <div className={styles.galleryHead}>
        <div>
          <h3 className={styles.galleryTitle}>{onggiScene.topic}</h3>
          <p className={styles.gallerySub}>같은 주제에서 다른 시선을 느껴보세요.</p>
        </div>
        <div className={styles.sorts} role="group" aria-label="정렬">
          {sorts.map((item) => (
            <button key={item.id} type="button" aria-pressed={sort === item.id} onClick={() => chooseSort(item.id)}>
              {item.label}
            </button>
          ))}
        </div>
      </div>

      <ul className={styles.gallery}>
        {ordered.map((card) => (
          <li key={card.id} className={card.kind === "mine" ? `${styles.card} ${styles.cardMine}` : styles.card}>
            <div className={styles.cardImage} style={{ backgroundImage: `url(${source})`, ...cropBackground(card.rect) }} role="img" aria-label={`${onggiScene.topic} — ${card.line}`}>
              <button type="button" className={styles.like} aria-pressed={liked.has(card.id)} aria-label={`좋아요 ${likesOf(card)}개`} onClick={() => toggleLike(card.id)}>
                <span className={styles.likeIcon} aria-hidden="true">
                  {liked.has(card.id) ? "♥" : "♡"}
                </span>
                <span className={styles.likeCount}>{likesOf(card)}</span>
              </button>
              {card.kind === "mine" && signature ? (
                // PNG data URL은 오프스크린 캔버스에서 생성한 체험 전용 이미지라 next/image 최적화 대상이 아니다.
                // eslint-disable-next-line @next/next/no-img-element
                <img className={styles.signImage} src={signature} alt="내 사인" />
              ) : card.sign ? (
                <span className={styles.sign} aria-hidden="true">
                  {card.sign}
                </span>
              ) : null}
            </div>
            <p className={card.titled ? styles.cardLine : `${styles.cardLine} ${styles.cardLineEmpty}`}>
              {card.kind === "mine" && <span className={styles.mineTag}>내 작품</span>}
              {card.kind === "visitor" && <span className={styles.visitorTag}>익명</span>}
              {card.line}
            </p>
          </li>
        ))}
      </ul>

      <div className={styles.revealCta}>
        <h3>내일은 새로운 주제가 열려요</h3>
        <p>여기선 우리가 준 풍경이었지만, 앱에서는 당신의 하루가 풍경이 돼요.</p>
        <StoreBadges className={styles.revealBadges} tone="dark" />
      </div>
      <button type="button" className={`${styles.secondary} ${styles.again}`} onClick={onAgain}>
        다시 찍어보기
      </button>
    </section>
  );
}
