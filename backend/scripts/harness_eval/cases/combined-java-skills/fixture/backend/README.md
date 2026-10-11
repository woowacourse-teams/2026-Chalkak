# 피드백 날짜 오류 수정 작업 자료

JDK 17 이상과 실제 JUnit 5·AssertJ를 로컬 캐시에서 준비한 오프라인 개발 자료다. Gradle·Spring·PostgreSQL 전체 프로젝트를 대신하지 않는다.

- 현재 업무는 #924 날짜 검증 오류 수정과 검증 클래스 분리다.
- 두 번째 단위인 관리자 검색은 미착수다.
- 날짜는 KST 기준으로 검증하고 전달된 LocalDate는 변환하지 않는다.
- 일반 메시지 팝업만 보여주므로 기존 BUSINESS_ERROR를 재사용한다.
- 기존 LegacyLabel은 외부 호환 이름이 있어 이 작업에서 변경하지 않는다.

검사 명령: `python3 scripts/check_fixture.py test`
실제 javac로 컴파일하고 JUnit Platform Launcher로 실행한다. 오류가 나면 통과로 보고하지 않는다.
