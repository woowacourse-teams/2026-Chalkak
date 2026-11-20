package com.chalkak.backend.admin.service.jev;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.jev.domain.JevModerationRun;
import com.chalkak.backend.jev.domain.JevTopicCategory;
import com.chalkak.backend.jev.repository.JevJobRepository;
import com.chalkak.backend.jev.repository.JevRunFilter;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.repository.PostRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminJevQueryService {

    private final JevJobRepository jobs;
    private final PostRepository posts;
    private final ImageUrlProvider images;

    public History list(
            JevRunFilter filter,
            int page,
            int pageSize
    ) {
        if (page < 0 || page > 10000 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "페이지 범위를 확인해 주세요.");
        }
        List<JevModerationRun> rows = jobs.findRuns(
                filter,
                page * pageSize,
                pageSize + 1
        );
        return new History(rows.stream().limit(pageSize).map(this::detail).toList(),
                rows.size() > pageSize);
    }

    public Detail get(UUID id) {
        return detail(jobs.findRun(id).orElseThrow(() -> new NotFoundException(
                ErrorCode.BUSINESS_ERROR, "검수 이력을 찾을 수 없습니다.")));
    }

    private Detail detail(JevModerationRun run) {
        JevTopicCategory category = run.getClassificationId() == null
                ? null
                : jobs.findCategory(run.getClassificationId()).orElse(null);
        var post = posts.findById(run.getPostId()).orElse(null);
        return new Detail(
                run,
                category,
                post == null ? null : post.getModerationStatus(),
                post == null ? null : post.getDeletedAt(),
                images.getUrl(run.getStorageKey()),
                Map.of(
                        "topic_fit",
                        "사진이 선택한 주제에 맞지 않는 것으로 판단되어 반려되었습니다.",
                        "image_safety",
                        "사진이 서비스의 사진 안전 기준에 맞지 않는 것으로 판단되어 반려되었습니다.",
                        "image_expression",
                        "사진에 이용 기준에 어긋나는 욕설·모욕적 표현 또는 손짓이 포함된 것으로 판단되어 반려되었습니다."
                )
        );
    }

    public record History(List<Detail> rows, boolean hasNext) {
    }

    public record Detail(
            JevModerationRun run,
            JevTopicCategory category,
            ModerationStatus postStatus,
            Instant postDeletedAt,
            String originalImageUrl,
            Map<String, String> rejectionSuggestions
    ) {
    }
}
