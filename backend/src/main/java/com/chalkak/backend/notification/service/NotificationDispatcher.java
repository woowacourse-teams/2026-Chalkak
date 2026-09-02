package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.NotificationChannel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NotificationDispatcher {

    private final Map<NotificationChannel, NotificationSender> senders;

    public NotificationDispatcher(List<NotificationSender> senders) {
        this.senders = new EnumMap<>(NotificationChannel.class);
        for (NotificationSender sender : senders) {
            NotificationSender previous = this.senders.put(
                    sender.supportedChannel(),
                    sender
            );
            if (previous != null) {
                throw new IllegalStateException(
                        "같은 알림 채널의 발송 구현을 중복 등록할 수 없습니다: "
                                + sender.supportedChannel()
                );
            }
        }
    }

    public NotificationSendResult dispatch(
            NotificationChannel channel,
            NotificationMessage message
    ) {
        NotificationSender sender = senders.get(channel);
        if (sender == null) {
            return NotificationSendResult.permanentFailure(
                    "NOTIFICATION_CHANNEL_UNSUPPORTED"
            );
        }
        return sender.send(message);
    }
}
