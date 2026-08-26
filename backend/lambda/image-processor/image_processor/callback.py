import hashlib
import hmac
import json
import time
from collections.abc import Mapping
from typing import Any
from urllib import request
from urllib.error import HTTPError

from image_processor.errors import PermanentCallbackError

NON_RETRYABLE_STATUSES = frozenset({400, 404, 405, 413, 414, 415})


class ProcessingCallbackClient:
    """
    이미지 종류별 백엔드 완료·실패 콜백 클라이언트.

    서명 payload가 본문 해시를 포함하므로 본문이 없는 사인 콜백과 EXIF를 싣는 포스트 콜백이 같은 계약을
    쓴다. 서명한 바이트와 전송하는 바이트가 반드시 같아야 하므로 직렬화는 한 번만 한다.
    """

    def __init__(
        self,
        kind: str,
        base_urls: Mapping[str, str],
        secret: str,
        timeout_seconds: float,
    ):
        self._kind = kind
        self._base_urls = {
            environment: base_url.rstrip("/")
            for environment, base_url in base_urls.items()
        }
        self._secret = secret.encode()
        self._timeout_seconds = timeout_seconds

    def complete(
        self,
        environment: str,
        upload_id: str,
        body: Mapping[str, Any] | None = None,
    ) -> None:
        self._post(environment, upload_id, "complete", body)

    def failed(
        self,
        environment: str,
        upload_id: str,
        body: Mapping[str, Any] | None = None,
    ) -> None:
        self._post(environment, upload_id, "failed", body)

    def _post(
        self,
        environment: str,
        upload_id: str,
        result: str,
        body: Mapping[str, Any] | None,
    ) -> None:
        base_url = self._base_urls.get(environment)
        if base_url is None:
            raise ValueError(f"unsupported image environment: {environment}")
        timestamp = str(int(time.time()))
        path = f"/internal/v1/{self._kind}/{upload_id}/{result}"
        encoded_body = _encode(body)
        body_hash = hashlib.sha256(encoded_body).hexdigest()
        payload = f"{timestamp}\nPOST\n{path}\n{body_hash}".encode()
        signature = "v1=" + hmac.new(
            self._secret,
            payload,
            hashlib.sha256,
        ).hexdigest()
        headers = {
            "X-Chalkak-Callback-Timestamp": timestamp,
            "X-Chalkak-Callback-Signature": signature,
        }
        if body is not None:
            headers["Content-Type"] = "application/json; charset=utf-8"
        callback_request = request.Request(
            url=f"{base_url}/{upload_id}/{result}",
            data=encoded_body,
            headers=headers,
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


def _encode(body: Mapping[str, Any] | None) -> bytes:
    """
    본문을 순수 ASCII로 직렬화한다. 서명은 바이트에 걸리는데 백엔드는 컨버터가 디코딩한 문자열을 다시
    UTF-8로 인코딩해 해시하므로, non-ASCII가 섞이면 charset 설정에 따라 서명이 어긋난다. 한국어 렌즈명
    같은 EXIF가 401 무한 재시도를 부르지 않도록 charset에 의존하지 않는 표현으로 보낸다.
    """
    if body is None:
        return b""
    return json.dumps(body, separators=(",", ":")).encode("ascii")
