import hashlib
import hmac
import time
from collections.abc import Mapping
from urllib import request
from urllib.error import HTTPError

from image_processor.errors import PermanentCallbackError


# 같은 요청을 다시 보내도 결과가 바뀌지 않는 상태들이다. 우리 코드나 설정이 잘못 만든 요청이므로
# SQS 재시도는 인보케이션만 소모한다. 401·403은 secret 롤링 교체 중 잠깐 날 수 있고 429·408은
# 명시적으로 재시도 대상이라 제외한다.
NON_RETRYABLE_STATUSES = frozenset({400, 404, 405, 413, 414, 415})


class SignatureProcessingCallbackClient:
    def __init__(
        self,
        base_urls: Mapping[str, str],
        secret: str,
        timeout_seconds: float,
    ):
        self._base_urls = {
            environment: base_url.rstrip("/")
            for environment, base_url in base_urls.items()
        }
        self._secret = secret.encode()
        self._timeout_seconds = timeout_seconds

    def complete(self, environment: str, upload_id: str) -> None:
        self._post(environment, upload_id, "complete")

    def failed(self, environment: str, upload_id: str) -> None:
        self._post(environment, upload_id, "failed")

    def _post(self, environment: str, upload_id: str, result: str) -> None:
        base_url = self._base_urls.get(environment)
        if base_url is None:
            raise ValueError(f"unsupported image environment: {environment}")
        timestamp = str(int(time.time()))
        path = f"/internal/v1/signature-processing/{upload_id}/{result}"
        body = b""
        body_hash = hashlib.sha256(body).hexdigest()
        payload = f"{timestamp}\nPOST\n{path}\n{body_hash}".encode()
        signature = "v1=" + hmac.new(
            self._secret,
            payload,
            hashlib.sha256,
        ).hexdigest()
        callback_request = request.Request(
            url=f"{base_url}/{upload_id}/{result}",
            data=body,
            headers={
                "X-Chalkak-Callback-Timestamp": timestamp,
                "X-Chalkak-Callback-Signature": signature,
            },
            method="POST",
        )

        try:
            with request.urlopen(
                callback_request,
                timeout=self._timeout_seconds,
            ):
                return
        except HTTPError as error:
            if error.code in NON_RETRYABLE_STATUSES:
                raise PermanentCallbackError(
                    f"backend callback rejected with HTTP {error.code}"
                ) from error
            raise
