from typing import Any
from urllib import request
from urllib.error import HTTPError
from urllib.parse import urlsplit

from image_processor.errors import ProcessingOutputConflictError


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


def verify_existing_output(
    s3_client: Any,
    bucket: str,
    key: str,
    expected: bytes,
) -> None:
    response = s3_client.get_object(Bucket=bucket, Key=key)
    content_length = response.get("ContentLength")
    body = response["Body"]
    try:
        if content_length is not None and content_length != len(expected):
            raise ProcessingOutputConflictError(
                f"existing processing output does not match current result: {key}"
            )
        actual = body.read(len(expected) + 1)
    finally:
        body.close()

    if actual != expected:
        raise ProcessingOutputConflictError(
            f"existing processing output does not match current result: {key}"
        )
