package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.notification.TopicPushScheduleResult;
import java.util.ArrayList;
import java.util.List;

public record TopicPushScheduleListResponse(List<TopicPushScheduleResponse> schedules) {

    public static TopicPushScheduleListResponse from(
            List<TopicPushScheduleResult> results) {
        List<TopicPushScheduleResponse> schedules = new ArrayList<>(results.size());
        for (TopicPushScheduleResult result : results) {
            schedules.add(TopicPushScheduleResponse.from(result));
        }
        return new TopicPushScheduleListResponse(schedules);
    }
}
