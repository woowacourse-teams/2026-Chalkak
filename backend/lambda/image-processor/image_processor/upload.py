from urllib import request
from urllib.error import HTTPError
from urllib.parse import urlsplit


class PresignedUploadClient:
    def __init__(self, timeout_seconds: float):
        self._timeout_seconds = timeout_seconds

    def upload(
        self,
        url: str,
        body: bytes,
        content_type: str,
        cache_control: str,
    ) -> bool:
        parsed = urlsplit(url)
        if parsed.scheme != "https" or not parsed.netloc:
            raise ValueError("presigned upload URL must use HTTPS")
        upload_request = request.Request(
            url=url,
            data=body,
            headers={
                "Content-Type": content_type,
                "Cache-Control": cache_control,
                "If-None-Match": "*",
            },
            method="PUT",
        )
        try:
            with request.urlopen(
                upload_request,
                timeout=self._timeout_seconds,
            ):
                return True
        except HTTPError as error:
            if error.code == 412:
                return False
            raise
