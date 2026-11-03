package com.chalkak.backend.support;

import com.chalkak.backend.admin.infrastructure.persistence.aireview.AdminAiReviewQueryRepositoryImpl;
import com.chalkak.backend.admin.infrastructure.persistence.audit.AdminAuditLogRepositoryImpl;
import com.chalkak.backend.admin.infrastructure.persistence.auth.AdminRepositoryImpl;
import com.chalkak.backend.admin.infrastructure.persistence.feedback.AdminFeedbackQueryRepositoryImpl;
import com.chalkak.backend.admin.infrastructure.persistence.post.AdminPostQueryRepositoryImpl;
import com.chalkak.backend.admin.infrastructure.persistence.topic.AdminTopicQueryRepositoryImpl;
import com.chalkak.backend.admin.infrastructure.persistence.user.AdminUserQueryRepositoryImpl;
import com.chalkak.backend.aireview.infrastructure.persistence.AiReviewRepositoryImpl;
import com.chalkak.backend.auth.infrastructure.persistence.apple.AppleAuthorizationRepositoryImpl;
import com.chalkak.backend.auth.infrastructure.persistence.apple.PendingAppleAuthorizationRepositoryImpl;
import com.chalkak.backend.auth.infrastructure.persistence.identity.SocialAccountRepositoryImpl;
import com.chalkak.backend.auth.infrastructure.persistence.signup.ConsumedSignupTokenRepositoryImpl;
import com.chalkak.backend.auth.infrastructure.persistence.token.UserRefreshTokenRepositoryImpl;
import com.chalkak.backend.banner.infrastructure.persistence.BannerRepositoryImpl;
import com.chalkak.backend.feedback.infrastructure.persistence.FeedbackRepositoryImpl;
import com.chalkak.backend.like.infrastructure.persistence.PostLikeCounterRepositoryImpl;
import com.chalkak.backend.like.infrastructure.persistence.PostLikeRepositoryImpl;
import com.chalkak.backend.notification.infrastructure.persistence.NoticeRepositoryImpl;
import com.chalkak.backend.notification.infrastructure.persistence.NotificationRepositoryImpl;
import com.chalkak.backend.post.infrastructure.persistence.PostRepositoryImpl;
import com.chalkak.backend.topic.infrastructure.persistence.TopicRepositoryImpl;
import com.chalkak.backend.user.infrastructure.persistence.UserRepositoryImpl;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/**
 * PostgreSQL 슬라이스가 같은 저장 계층 구성을 재사용한다. 서비스와 외부 연동은 로딩하지 않으며 각 테스트의 트랜잭션은 개별
 * 롤백된다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        AdminAiReviewQueryRepositoryImpl.class,
        AdminAuditLogRepositoryImpl.class,
        AdminRepositoryImpl.class,
        AdminFeedbackQueryRepositoryImpl.class,
        AdminPostQueryRepositoryImpl.class,
        AdminTopicQueryRepositoryImpl.class,
        AdminUserQueryRepositoryImpl.class,
        AiReviewRepositoryImpl.class,
        AppleAuthorizationRepositoryImpl.class,
        PendingAppleAuthorizationRepositoryImpl.class,
        SocialAccountRepositoryImpl.class,
        ConsumedSignupTokenRepositoryImpl.class,
        UserRefreshTokenRepositoryImpl.class,
        BannerRepositoryImpl.class,
        FeedbackRepositoryImpl.class,
        PostLikeCounterRepositoryImpl.class,
        PostLikeRepositoryImpl.class,
        NoticeRepositoryImpl.class,
        NotificationRepositoryImpl.class,
        PostRepositoryImpl.class,
        TopicRepositoryImpl.class,
        UserRepositoryImpl.class
})
public @interface PostgresRepositoryTest {
}
