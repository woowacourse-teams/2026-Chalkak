import { NoticeEditScreen } from "@/features/notices/ui/notice-form-screen";

export default async function EditNoticePage({ params }: { params: Promise<{ noticeId: string }> }) {
  const { noticeId } = await params;
  return <NoticeEditScreen noticeId={noticeId} />;
}
