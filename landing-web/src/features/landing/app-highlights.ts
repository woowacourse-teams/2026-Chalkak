/** 디자인 시안들이 같은 문구로 보여 주는 앱의 감상·기록 기능. 기존 사용법·자주 묻는 질문 문구에서 나눠 썼다. */
export const appHighlights = [
  { id: "sort", title: "세 가지 방식으로 감상", body: "같은 주제의 작품을 최신순·인기순·랜덤순으로 둘러봐요." },
  { id: "album", title: "앨범 사진으로도 참여", body: "주제가 열려 있는 동안이라면 앨범에 있던 사진으로도 참여할 수 있어요." },
  { id: "like", title: "좋아요로 남기는 감상", body: "마음에 드는 작품에 좋아요를 남겨요." },
  { id: "calendar", title: "달력에 쌓이는 기록", body: "내가 참여한 날은 달력에 모여 다시 꺼내볼 수 있어요." },
] as const;

export const appHighlightsTitle = ["사진 실력보다 중요한 건,", "당신의 시선이에요"] as const;
