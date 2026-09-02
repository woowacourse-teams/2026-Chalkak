package com.chalkak.backend.config;

import com.chalkak.backend.admin.infrastructure.infra.AdminAuthenticationProperties;
import com.chalkak.backend.auth.api.support.AuthenticationErrorResponder;
import com.chalkak.backend.auth.api.support.ForbiddenAccessDeniedHandler;
import com.chalkak.backend.auth.api.support.UnauthorizedEntryPoint;
import com.chalkak.backend.auth.domain.AccessTokenScope;
import com.chalkak.backend.auth.infrastructure.infra.access.JwtAccessTokenProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(AdminAuthenticationProperties.class)
public class SecurityConfig {

    /**
     * 목록 조회만 공개다. {@code /api/v1/posts/*}처럼 넓게 잡으면 상세 조회와 캘린더까지 열린다.
     * 둘 다 컨트롤러에서 로그인을 요구하므로 경로 규칙도 같은 판단을 하게 둔다.
     */
    private static final String[] PUBLIC_GET_PATHS = {
            "/api/v1/topics",
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
            AuthenticationErrorResponder responder,
            AdminAuthenticationProperties adminAuthenticationProperties,
            Environment environment
    ) throws Exception {
        validateDevelopmentBypass(adminAuthenticationProperties, environment);
        UnauthorizedEntryPoint entryPoint = new UnauthorizedEntryPoint(responder);

        return http
                // 자격증명을 쿠키가 아니라 Authorization 헤더로 받는 stateless API라
                // CSRF 공격이 성립하지 않는다. 켜 두면 모든 쓰기 요청이 403이 된다.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(request -> {
                    request
                        // 로그인과 회원가입은 토큰을 받기 전에 호출한다.
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // 관리자 로그인만 관리자 토큰 발급 전에 호출한다.
                        .requestMatchers("/api/v1/admin/auth/login").permitAll()
                        // 이미지 처리 Lambda 콜백은 HMAC으로 자체 인증한다. 여기서 막으면
                        // 처리 결과가 영원히 반영되지 않는다.
                        .requestMatchers("/internal/v1/**").permitAll()
                        // 막으면 헬스체크가 실패해 배포가 중단된다.
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // 예외 처리기가 잡지 못한 오류가 컨테이너를 통해 여기로 넘어온다.
                        // 막으면 익명 요청의 장애가 401로 뒤바뀐다.
                        .requestMatchers("/error").permitAll();
                    if (adminAuthenticationProperties.developmentBypassEnabled()) {
                        // local/test에서 명시적으로 켠 경우에만 고정 개발 관리자를 사용한다.
                        request.requestMatchers("/api/v1/admin/**").permitAll();
                    }
                    request.requestMatchers("/api/v1/admin/**")
                            .hasAuthority(AccessTokenScope.ADMIN.toAuthority());
                    request.requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS).permitAll()
                        // 새 API에 인증을 붙이는 것을 잊어도 기본값이 차단이 되도록 한다.
                        .anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(entryPoint)
                        .jwt(jwt -> jwt.decoder(accessTokenProvider.jwtDecoder())))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private void validateDevelopmentBypass(
            AdminAuthenticationProperties properties,
            Environment environment
    ) {
        if (properties.developmentBypassEnabled()
                && !environment.acceptsProfiles(Profiles.of("!dev & !prod & (local | test)"))) {
            throw new IllegalStateException("개발 관리자 인증 우회는 local/test에서만 사용할 수 있습니다.");
        }
    }
}
