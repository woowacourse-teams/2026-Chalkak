"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { queryKeys } from "@/shared/query/query-client";

import {
  deleteAdminFeedback,
  deleteAdminFeedbackAnswer,
  fetchAdminFeedback,
  fetchAdminFeedbacks,
  registerAdminFeedbackAnswer,
  type AdminFeedbackFilters,
} from "./feedback-api";

export function useAdminFeedbacks(filters: AdminFeedbackFilters) {
  return useQuery({
    queryKey: queryKeys.feedbacks.list(filters),
    queryFn: ({ signal }) => fetchAdminFeedbacks(filters, signal),
  });
}

export function useAdminFeedback(feedbackId: string) {
  return useQuery({
    queryKey: queryKeys.feedbacks.detail(feedbackId),
    queryFn: ({ signal }) => fetchAdminFeedback(feedbackId, signal),
  });
}

type FeedbackChange =
  | { kind: "register-answer"; content: string; sendPush?: boolean }
  | { kind: "delete-answer"; answerVersion: string }
  | { kind: "delete-inquiry" };

export function useAdminFeedbackChange(feedbackId: string) {
  const client = useQueryClient();
  return useMutation({
    retry: false,
    mutationFn: (change: FeedbackChange) => {
      switch (change.kind) {
        case "register-answer":
          return registerAdminFeedbackAnswer(feedbackId, change.content, change.sendPush);
        case "delete-answer":
          return deleteAdminFeedbackAnswer(feedbackId, change.answerVersion);
        case "delete-inquiry":
          return deleteAdminFeedback(feedbackId);
      }
    },
    onSettled: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: queryKeys.feedbacks.detail(feedbackId) }),
        client.invalidateQueries({ queryKey: queryKeys.feedbacks.lists }),
        client.invalidateQueries({ queryKey: queryKeys.auditLogs }),
      ]);
    },
  });
}
