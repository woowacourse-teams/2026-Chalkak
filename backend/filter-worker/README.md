# chalkak-filter-worker

작가가 Lightroom 프리셋을 적용해 내보낸 견본 JPG(3072x4096)를 필터 레시피로 바꾸는 Node 워커입니다.

현재는 입력 단계(바이트 검사와 디코딩)만 있고, 추출 단계는 이어지는 작업에서 추가합니다.

## 테스트

Node 22 이상이 필요합니다.

```
npm ci && npm test
```
