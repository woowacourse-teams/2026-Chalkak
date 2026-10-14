import { NoticeDetailScreen } from "@/features/notices/ui/notice-detail-screen";

export default async function NoticePage({ params }: { params: Promise<{ noticeId: string }> }) {
  const { noticeId } = await params;
  return <NoticeDetailScreen noticeId={noticeId} />;
}
