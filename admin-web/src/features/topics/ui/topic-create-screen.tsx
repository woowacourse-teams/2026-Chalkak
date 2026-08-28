"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCreateAdminTopic } from "../api/topic-hooks";
import { getTopicErrorMessage } from "../model/topic-display";
import { TopicForm } from "./topic-form";
import { useToast } from "@/shared/ui/toast";
import styles from "@/shared/ui/management.module.css";
export function TopicCreateScreen(){const router=useRouter();const mutation=useCreateAdminTopic();const{showToast}=useToast();return <div className={styles.page}><Link className={styles.back} href="/topics">← 주제 목록으로 돌아가기</Link><section className={styles.detailHero}><div><p>NEW TOPIC</p><h2>새 주제 등록</h2><span>한국 날짜와 참여 기간을 정확히 입력해 주세요.</span></div></section><TopicForm pending={mutation.isPending} submitLabel="주제 등록" onSubmit={async body=>{try{const topic=await mutation.mutateAsync(body);showToast("주제를 등록했습니다.","success");router.push("/topics/"+topic.topicId)}catch(error){showToast(getTopicErrorMessage(error),"error")}}}/></div>}
