// 서버 전용. 방문자 작품을 Neon Postgres에 저장한다. 로컬 개발에서 DB 주소가 없으면 메모리에 둔다.

import { neon } from "@neondatabase/serverless";

import type { Rect } from "./camera";
import type { SharedShot } from "./shared-shot";

/** 최근 이만큼만 남기고 오래된 작품은 지운다. */
const KEEP_LATEST = 1000;

export type ShotStore = {
  add(rect: Rect, title: string | null): Promise<SharedShot>;
  latest(limit: number): Promise<SharedShot[]>;
  /** 최근 seconds초 동안 저장된 작품 수. 전체 요청량 제한에 쓴다. */
  countSince(seconds: number): Promise<number>;
};

type Row = { id: string; x: number; y: number; w: number; h: number; title: string | null; created_at: string | Date };

function toShot(row: Row): SharedShot {
  return {
    id: String(row.id),
    rect: { x: Number(row.x), y: Number(row.y), width: Number(row.w), height: Number(row.h) },
    title: row.title,
    createdAt: new Date(row.created_at).toISOString(),
  };
}

function neonStore(url: string): ShotStore {
  const sql = neon(url);
  // 인스턴스마다 처음 한 번만 테이블을 확인한다.
  let ready: Promise<unknown> | null = null;
  const ensure = () =>
    (ready ??= sql`
      CREATE TABLE IF NOT EXISTS landing_shots (
        id BIGSERIAL PRIMARY KEY,
        x REAL NOT NULL,
        y REAL NOT NULL,
        w REAL NOT NULL,
        h REAL NOT NULL,
        title TEXT,
        created_at TIMESTAMPTZ NOT NULL DEFAULT now()
      )
    `.catch((error) => {
      ready = null;
      throw error;
    }));

  return {
    async add(rect, title) {
      await ensure();
      const rows = (await sql`
        INSERT INTO landing_shots (x, y, w, h, title)
        VALUES (${rect.x}, ${rect.y}, ${rect.width}, ${rect.height}, ${title})
        RETURNING id, x, y, w, h, title, created_at
      `) as Row[];
      await sql`DELETE FROM landing_shots WHERE id <= (SELECT id FROM landing_shots ORDER BY id DESC OFFSET ${KEEP_LATEST} LIMIT 1)`;
      return toShot(rows[0]);
    },
    async latest(limit) {
      await ensure();
      const rows = (await sql`SELECT id, x, y, w, h, title, created_at FROM landing_shots ORDER BY id DESC LIMIT ${limit}`) as Row[];
      return rows.map(toShot);
    },
    async countSince(seconds) {
      await ensure();
      const rows = (await sql`SELECT count(*)::int AS n FROM landing_shots WHERE created_at > now() - make_interval(secs => ${seconds})`) as { n: number }[];
      return rows[0]?.n ?? 0;
    },
  };
}

function memoryStore(): ShotStore {
  const shots: SharedShot[] = [];
  let next = 1;
  return {
    async add(rect, title) {
      const shot = { id: String(next++), rect, title, createdAt: new Date().toISOString() };
      shots.unshift(shot);
      shots.length = Math.min(shots.length, KEEP_LATEST);
      return shot;
    },
    async latest(limit) {
      return shots.slice(0, limit);
    },
    async countSince(seconds) {
      const since = Date.now() - seconds * 1000;
      return shots.filter((shot) => Date.parse(shot.createdAt) > since).length;
    },
  };
}

let store: ShotStore | null | undefined;

/** Vercel의 Neon 연동이 넣어 주는 주소를 쓴다. 배포 환경에서 주소가 없으면 공유를 끈다(null). */
export function shotStore(env: Record<string, string | undefined> = process.env): ShotStore | null {
  if (store !== undefined) return store;
  const url = env.DATABASE_URL ?? env.POSTGRES_URL;
  if (url) store = neonStore(url);
  else store = env.NODE_ENV === "production" ? null : memoryStore();
  return store;
}
