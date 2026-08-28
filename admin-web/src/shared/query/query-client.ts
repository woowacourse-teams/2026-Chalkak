import { QueryClient } from "@tanstack/react-query";

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: 1,
        staleTime: 30_000,
        refetchOnWindowFocus: false,
      },
      mutations: {
        retry: false,
      },
    },
  });
}

export const queryKeys = {
  posts: {
    all: ["admin", "posts"] as const,
    lists: ["admin", "posts", "list"] as const,
    list: (filters: object) =>
      [...queryKeys.posts.lists, filters] as const,
    detail: (postId: string) =>
      [...queryKeys.posts.all, "detail", postId] as const,
  },
  users: {
    all: ["admin", "users"] as const,
  },
  topics: {
    all: ["admin", "topics"] as const,
  },
  dashboard: ["admin", "dashboard"] as const,
  auditLogs: ["admin", "audit-logs"] as const,
  pushes: ["admin", "pushes"] as const,
};
