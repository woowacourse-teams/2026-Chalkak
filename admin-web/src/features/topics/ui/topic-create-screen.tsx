"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef } from "react";

import { useCreateAdminTopic } from "../api/topic-hooks";
import { getTopicErrorMessage } from "../model/topic-display";
import { TopicForm } from "./topic-form";
import { useToast } from "@/shared/ui/toast";
import styles from "@/shared/ui/management.module.css";

export function TopicCreateScreen() {
  const router = useRouter();
  const mutation = useCreateAdminTopic();
  const { showToast } = useToast();
  const lock = useRef(false);

  return (
    <div className={styles.page}>
      <Link className={styles.back} href="/topics">← 주제 목록으로 돌아가기</Link>
      <h2 className={styles.pageTitle}>새 주제</h2>
      <TopicForm
        pending={mutation.isPending}
        submitLabel="주제 등록"
        onSubmit={async (body) => {
          if (lock.current || mutation.isPending) return;
          lock.current = true;
          try {
            const topic = await mutation.mutateAsync(body);
            showToast("주제를 등록했습니다.", "success");
            router.push("/topics/" + topic.topicId);
          } catch (error) {
            showToast(getTopicErrorMessage(error), "error");
          } finally { lock.current = false; }
        }}
      />
    </div>
  );
}
