import io
import logging
import re
import warnings
from dataclasses import dataclass
from typing import Any

from botocore.exceptions import ClientError
from PIL import Image, UnidentifiedImageError

from image_processor.config import Settings
from image_processor.errors import PermanentCallbackError, RejectedImageError
from image_processor.events import S3ObjectCreated

PNG_CONTENT_TYPE = "image/png"


@dataclass(frozen=True)
class ProcessedSignature:
    original_key: str
    thumbnail_key: str


LOGGER = logging.getLogger(__name__)

class SignatureImageProcessor:
    def __init__(self, s3_client: Any, settings: Settings, callback_client: Any):
        self._s3_client = s3_client
        self._settings = settings
        self._callback_client = callback_client
        self._staging_key_pattern = re.compile(
            rf"^{re.escape(settings.root_prefix)}/staging/"
            r"(?P<environment>dev|prod)/signatures/"
            r"(?P<upload_id>[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
            r"[0-9a-f]{4}-[0-9a-f]{12})\.png$"
        )

    def process(self, event: S3ObjectCreated) -> ProcessedSignature:
        self._validate_bucket(event)
        environment, upload_id = self._extract_staging_identity(event.key)
        try:
            self._validate_size(event)
            source = self._download(event)
            original, thumbnail = self._decode_and_transform(source)

            original_key = self._destination_key(environment, "original", upload_id)
            thumbnail_key = self._destination_key(environment, "thumbnail", upload_id)

            self._upload(event.bucket, original_key, original)
            self._upload(event.bucket, thumbnail_key, thumbnail)
        except RejectedImageError:
            # 실패 콜백이 전달된 반려 이미지는 재처리하지 않으므로 staging을 삭제한다.
            # 콜백이 실패하면 재시도에 필요하므로 staging을 유지한다.
            try:
                self._callback_client.failed(environment, upload_id)
            except PermanentCallbackError:
                LOGGER.error(
                    "failed callback permanently refused for %s",
                    upload_id,
                )
            else:
                self._s3_client.delete_object(
                    Bucket=event.bucket,
                    Key=event.key,
                )
            raise

        self._callback_client.complete(environment, upload_id)
        self._s3_client.delete_object(Bucket=event.bucket, Key=event.key)

        return ProcessedSignature(
            original_key=original_key,
            thumbnail_key=thumbnail_key,
        )

    def _validate_bucket(self, event: S3ObjectCreated) -> None:
        if event.bucket != self._settings.expected_bucket:
            raise RejectedImageError("image was uploaded to an unexpected bucket")

    def _validate_size(self, event: S3ObjectCreated) -> None:
        if event.size is not None and event.size > self._settings.max_input_bytes:
            raise RejectedImageError("image exceeds the maximum input size")

    def _extract_staging_identity(self, key: str) -> tuple[str, str]:
        match = self._staging_key_pattern.fullmatch(key)
        if match is None:
            raise RejectedImageError("object key is not a signature staging key")
        return match.group("environment"), match.group("upload_id")

    def _download(self, event: S3ObjectCreated) -> bytes:
        try:
            response = self._s3_client.get_object(Bucket=event.bucket, Key=event.key)
        except ClientError as exception:
            if exception.response.get("Error", {}).get("Code") in {
                "NoSuchKey",
                "NotFound",
                "404",
            }:
                raise RejectedImageError(
                    "staging image no longer exists"
                ) from exception
            raise

        content_length = response.get("ContentLength")
        if (
            content_length is not None
            and content_length > self._settings.max_input_bytes
        ):
            raise RejectedImageError("image exceeds the maximum input size")

        body = response["Body"]
        try:
            source = body.read(self._settings.max_input_bytes + 1)
        finally:
            body.close()

        if len(source) > self._settings.max_input_bytes:
            raise RejectedImageError("image exceeds the maximum input size")
        return source

    def _decode_and_transform(self, source: bytes) -> tuple[bytes, bytes]:
        try:
            with warnings.catch_warnings():
                warnings.simplefilter("error", Image.DecompressionBombWarning)
                Image.MAX_IMAGE_PIXELS = self._settings.max_image_pixels

                with Image.open(io.BytesIO(source)) as candidate:
                    image_format = candidate.format
                    candidate.verify()

                if image_format != "PNG":
                    raise RejectedImageError("signature image is not a PNG")

                with Image.open(io.BytesIO(source)) as decoded:
                    decoded.load()
                    sanitized = self._sanitize(decoded)
        except RejectedImageError:
            raise
        except (
            Image.DecompressionBombError,
            Image.DecompressionBombWarning,
            UnidentifiedImageError,
            OSError,
            SyntaxError,
            ValueError,
        ) as exception:
            raise RejectedImageError(
                "signature image cannot be decoded safely"
            ) from exception

        original = self._encode_png(sanitized)
        thumbnail_image = sanitized.copy()
        thumbnail_image.thumbnail(
            (
                self._settings.thumbnail_max_size,
                self._settings.thumbnail_max_size,
            ),
            Image.Resampling.LANCZOS,
        )
        thumbnail = self._encode_png(thumbnail_image)
        return original, thumbnail

    @staticmethod
    def _sanitize(image: Image.Image) -> Image.Image:
        has_alpha = image.mode in {"RGBA", "LA"} or "transparency" in image.info
        return image.convert("RGBA" if has_alpha else "RGB")

    @staticmethod
    def _encode_png(image: Image.Image) -> bytes:
        output = io.BytesIO()
        image.save(output, format="PNG", optimize=True)
        return output.getvalue()

    def _destination_key(
        self,
        environment: str,
        variant: str,
        upload_id: str,
    ) -> str:
        return (
            f"{self._settings.root_prefix}/signatures/"
            f"{environment}/{variant}/{upload_id}.png"
        )

    def _upload(self, bucket: str, key: str, body: bytes) -> None:
        self._s3_client.put_object(
            Bucket=bucket,
            Key=key,
            Body=body,
            ContentType=PNG_CONTENT_TYPE,
            CacheControl=self._settings.cache_control,
        )
