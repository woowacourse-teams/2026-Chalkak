package com.chalkak.backend.config;

import com.chalkak.backend.auth.api.support.AuthenticationErrorResponder;
import com.chalkak.backend.auth.api.support.ForbiddenAccessDeniedHandler;
import com.chalkak.backend.auth.api.support.UnauthorizedEntryPoint;
import com.chalkak.backend.auth.infrastructure.infra.access.JwtAccessTokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    /**
     * 목록 조회만 공개다. {@code /api/v1/posts/*}처럼 넓게 잡으면 상세 조회와 캘린더까지 열린다.
     * 둘 다 컨트롤러에서 로그인을 요구하므로 경로 규칙도 같은 판단을 하게 둔다.
     */
    private static final String[] PUBLIC_GET_PATHS = {
            "/api/v1/topics",
            "/api/v1/topics/**",
            "/api/v1/posts"
    };

    /**
     * 이 빈이 없으면 Spring Boot의 리소스 서버 자동 구성이 스스로 필터 체인을 만들면서
     * {@code JwtDecoder} 빈을 타입으로 찾는다. OIDC 검증용 {@code googleJwtDecoder}와
     * {@code kakaoJwtDecoder}가 이미 있어 후보가 둘 이상이면 기동이 실패한다.
     * 액세스 토큰 디코더는 반드시 여기서 명시적으로 넘긴다.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAccessTokenProvider accessTokenProvider,
            AuthenticationErrorResponder responder
    ) throws Exception {
        return http
                // 자격증명을 쿠키가 아니라 Authorization 헤더로 받는 stateless API라
                // CSRF 공격이 성립하지 않는다. 켜 두면 모든 쓰기 요청이 403이 된다.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(request -> request
                        // 로그인과 회원가입은 토큰을 받기 전에 호출한다.
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // 이미지 처리 Lambda 콜백은 HMAC으로 자체 인증한다. 여기서 막으면
                        // 처리 결과가 영원히 반영되지 않는다.
                        .requestMatchers("/internal/v1/**").permitAll()
                        // 막으면 헬스체크가 실패해 배포가 중단된다.
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS).permitAll()
                        // 새 API에 인증을 붙이는 것을 잊어도 기본값이 차단이 되도록 한다.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(
                                new UnauthorizedEntryPoint(responder))
                        .jwt(jwt -> jwt.decoder(accessTokenProvider.jwtDecoder())))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(
                                new UnauthorizedEntryPoint(responder))
                        .accessDeniedHandler(
                                new ForbiddenAccessDeniedHandler(responder)))
                .build();
    }

    @Bean
    public AuthenticationErrorResponder authenticationErrorResponder(
            ObjectMapper objectMapper
    ) {
        return new AuthenticationErrorResponder(objectMapper);
    }
}
