import hashlib
import hmac
import time
from collections.abc import Mapping
from urllib import request


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

        with request.urlopen(
            callback_request,
            timeout=self._timeout_seconds,
        ) as response:
            if not 200 <= response.status < 300:
                raise RuntimeError(
                    f"backend callback returned HTTP {response.status}"
                )
