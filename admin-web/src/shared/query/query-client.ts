import { QueryClient } from "@tanstack/react-query";
import { ApiError } from "@/shared/api/errors";

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: (failureCount, error) => {
          if (error instanceof ApiError && (error.status === 401 || error.status === 403)) return false;
          return failureCount < 1;
        },
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
    lists: ["admin", "users", "list"] as const,
    list: (filters: object) => [...queryKeys.users.lists, filters] as const,
    detail: (userId: string) =>
      [...queryKeys.users.all, "detail", userId] as const,
  },
  topics: {
    all: ["admin", "topics"] as const,
    lists: ["admin", "topics", "list"] as const,
    list: (filters: object) => [...queryKeys.topics.lists, filters] as const,
    detail: (topicId: string) =>
      [...queryKeys.topics.all, "detail", topicId] as const,
  },
  dashboard: ["admin", "dashboard"] as const,
  auditLogs: ["admin", "audit-logs"] as const,
  pushes: ["admin", "pushes"] as const,
};
