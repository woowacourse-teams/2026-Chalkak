// 표준 출력에 한 줄짜리 JSON을 쓰는 로거(README 로그 절). 시크릿·서명·요청 헤더는 호출하는 쪽에서 넘기지 않는다.
// 필드 이름이 time·level·event와 겹치면 공통 필드가 이긴다.
const LEVELS = ['info', 'warn', 'error'];

export function createLogger({ write = (line) => process.stdout.write(line), now = () => new Date() } = {}) {
  const logger = {};
  for (const level of LEVELS) {
    logger[level] = (event, fields = {}) => {
      let line;
      try {
        line = JSON.stringify({ ...fields, time: now().toISOString(), level, event });
      } catch {
        // 순환 참조 같은 직렬화 실패로 워커가 멈추지 않게 한다
        line = JSON.stringify({ time: now().toISOString(), level, event, logError: 'fields_not_serializable' });
      }
      write(`${line}\n`);
    };
  }
  return logger;
}
