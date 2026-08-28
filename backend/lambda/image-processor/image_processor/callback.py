import hashlib
import hmac
import json
import time
from collections.abc import Mapping
from dataclasses import dataclass
from typing import Any
from urllib import request
from urllib.error import HTTPError
from urllib.parse import urlsplit

from image_processor.errors import PermanentCallbackError

NON_RETRYABLE_STATUSES = frozenset({400, 404, 405, 413, 414, 415})


@dataclass(frozen=True)
class ProcessingUploadUrls:
    original_upload_url: str
    thumbnail_upload_url: str
    content_type: str
    cache_control: str


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
        self._path_prefix = f"/internal/v1/{kind}"
        api_base_urls = {
            environment: base_url.rstrip("/")
            for environment, base_url in base_urls.items()
        }
        self._validate_api_base_urls(api_base_urls)
        self._base_urls = {
            environment: f"{base_url}/{kind}"
            for environment, base_url in api_base_urls.items()
        }
        self._secret = secret.encode()
        self._timeout_seconds = timeout_seconds

    def _validate_api_base_urls(self, api_base_urls: Mapping[str, str]) -> None:
        """
        백엔드는 서명 대상 경로를 자기 컨트롤러 상수로 재구성한다. base URL 경로가 그와 다르면 모든 콜백이
        401을 받는데, 401은 시크릿 롤링을 고려해 재시도 대상이라 배포 오타 하나가 무한 재시도로 나타난다.
        기동 시점에 걸러 낸다.
        """
        for environment, base_url in api_base_urls.items():
            parsed = urlsplit(base_url)
            try:
                hostname = parsed.hostname
                _ = parsed.port
            except ValueError as exception:
                raise ValueError(
                    f"{environment} image processing API base URL is invalid"
                ) from exception
            if (
                parsed.scheme != "https"
                or not hostname
                or parsed.username is not None
                or parsed.password is not None
                or parsed.query
                or parsed.fragment
            ):
                raise ValueError(
                    f"{environment} image processing API base URL must be "
                    "HTTPS without credentials, query, or fragment"
                )
            if parsed.path != "/internal/v1":
                raise ValueError(
                    f"{environment} image processing API base URL path must be "
                    "/internal/v1"
                )

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

    def issue_upload_urls(
        self,
        environment: str,
        upload_id: str,
    ) -> ProcessingUploadUrls:
        try:
            raw_response = self._post(
                environment,
                upload_id,
                "upload-urls",
                None,
                permanent_rejection=False,
            )
        except HTTPError as error:
            if error.code == 404:
                raise PermanentCallbackError(
                    "backend cancelled processing upload URLs with HTTP 404"
                ) from error
            raise
        try:
            payload = json.loads(raw_response)
            upload_urls = ProcessingUploadUrls(
                original_upload_url=payload["originalUploadUrl"],
                thumbnail_upload_url=payload["thumbnailUploadUrl"],
                content_type=payload["contentType"],
                cache_control=payload["cacheControl"],
            )
        except (KeyError, TypeError, ValueError) as exception:
            raise ValueError("backend returned invalid processing upload URLs") from exception
        self._validate_upload_urls(upload_urls)
        return upload_urls

    def _post(
        self,
        environment: str,
        upload_id: str,
        result: str,
        body: Mapping[str, Any] | None,
        permanent_rejection: bool = True,
    ) -> bytes:
        base_url = self._base_urls.get(environment)
        if base_url is None:
            raise ValueError(f"unsupported image environment: {environment}")
        timestamp = str(int(time.time()))
        # 서명하는 경로와 요청하는 URL이 같은 출처에서 나와야 둘이 어긋나지 않는다.
        path = f"{urlsplit(base_url).path}/{upload_id}/{result}"
        encoded_body = _encode(body)
        body_hash = hashlib.sha256(encoded_body).hexdigest()
        # 서명 대상에 대상 환경을 넣는다. dev와 prod가 같은 비밀키를 쓰는 동안에는, 환경을 묶지 않으면
        # dev용으로 만든 서명이 prod 백엔드에서도 그대로 유효하다.
        payload = f"{timestamp}\nPOST\n{path}\n{body_hash}\n{environment}".encode()
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
            ) as response:
                return response.read()
        except HTTPError as error:
            if permanent_rejection and error.code in NON_RETRYABLE_STATUSES:
                raise PermanentCallbackError(
                    f"backend callback rejected with HTTP {error.code}"
                ) from error
            raise

    @staticmethod
    def _validate_upload_urls(upload_urls: ProcessingUploadUrls) -> None:
        for value in (
            upload_urls.original_upload_url,
            upload_urls.thumbnail_upload_url,
            upload_urls.content_type,
            upload_urls.cache_control,
        ):
            if not isinstance(value, str) or not value:
                raise ValueError("backend returned invalid processing upload URLs")
        for url in (
            upload_urls.original_upload_url,
            upload_urls.thumbnail_upload_url,
        ):
            parsed = urlsplit(url)
            if parsed.scheme != "https" or not parsed.netloc:
                raise ValueError("processing upload URL must use HTTPS")


def _encode(body: Mapping[str, Any] | None) -> bytes:
    """
    본문을 순수 ASCII로 직렬화한다. 서명은 바이트에 걸리는데 백엔드는 컨버터가 디코딩한 문자열을 다시
    UTF-8로 인코딩해 해시하므로, non-ASCII가 섞이면 charset 설정에 따라 서명이 어긋난다. 한국어 렌즈명
    같은 EXIF가 401 무한 재시도를 부르지 않도록 charset에 의존하지 않는 표현으로 보낸다.
    """
    if body is None:
        return b""
    return json.dumps(body, separators=(",", ":")).encode("ascii")
