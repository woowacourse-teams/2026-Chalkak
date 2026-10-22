import { useSyncExternalStore } from "react";

const subscribe = () => () => {};

/** 서버 렌더링에서는 fallback을, 브라우저에서는 read()의 값을 쓴다. 바뀌지 않는 값에만 쓴다. */
export function useClientValue<T extends string | number | boolean>(read: () => T, fallback: T): T {
  return useSyncExternalStore(subscribe, read, () => fallback);
}
