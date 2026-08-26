import io
import json
import logging
import math
import re
import warnings
from dataclasses import dataclass
from typing import Any

from botocore.exceptions import ClientError
from PIL import Image, ImageOps, UnidentifiedImageError
from PIL.ExifTags import TAGS

from image_processor.config import Settings
from image_processor.errors import PermanentCallbackError, RejectedImageError
from image_processor.events import S3ObjectCreated

WEBP_CONTENT_TYPE = "image/webp"

GPS_IFD_TAG = 0x8825
EXIF_IFD_TAG = 0x8769
ORIENTATION_TAG = 0x0112
DATE_TIME_ORIGINAL_TAG = 0x9003
OFFSET_TIME_ORIGINAL_TAG = 0x9011
GPS_LATITUDE_REF = 1
GPS_LATITUDE = 2
GPS_LONGITUDE_REF = 3
GPS_LONGITUDE = 4
MAX_LATITUDE = 90.0
MAX_LONGITUDE = 180.0

# 별도 필드로 싣거나 출력 이미지에 이미 반영한 태그는 metaAttributes에서 뺀다. IFD 포인터는 바이트 오프셋일
# 뿐이라 값으로서 의미가 없고, Orientation은 exif_transpose가 픽셀에 적용한 뒤라 그대로 두면 이중 회전을 부른다.
EXCLUDED_META_TAGS = frozenset({
    GPS_IFD_TAG,
    EXIF_IFD_TAG,
    ORIENTATION_TAG,
    DATE_TIME_ORIGINAL_TAG,
    OFFSET_TIME_ORIGINAL_TAG,
})

# 잘림 표시가 차지하는 바이트. 한도 검사에 미리 반영해 결과가 한도를 넘지 않게 한다.
_TRUNCATED_MARKER_BYTES = len(b',"_truncated":true')

LOGGER = logging.getLogger(__name__)


class RejectedPostImageError(RejectedImageError):
    """거절 사유를 백엔드 콜백으로 전달하기 위해 reason 코드를 함께 들고 다닌다."""

    def __init__(self, reason: str, message: str):
        super().__init__(message)
        self.reason = reason


@dataclass(frozen=True)
class ProcessedPostImage:
    original_key: str
    thumbnail_key: str


class PostImageProcessor:
    """
    포스트 이미지 검증·변환 프로세서.

    사인과 달리 WebP만 받고 해상도를 제한하지 않는다. EXIF는 백엔드로 보내 DB에 남기지만 출력 객체에는
    싣지 않는다. 위치와 기종 정보가 공개 이미지에 남으면 안 되기 때문이다.
    """

    def __init__(self, s3_client: Any, settings: Settings, callback_client: Any):
        self._s3_client = s3_client
        self._settings = settings
        self._callback_client = callback_client
        self._staging_key_pattern = re.compile(
            rf"^{re.escape(settings.root_prefix)}/staging/"
            r"(?P<environment>dev|prod)/posts/"
            r"(?P<upload_id>[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
            r"[0-9a-f]{4}-[0-9a-f]{12})\.webp$"
        )

    def process(self, event: S3ObjectCreated) -> ProcessedPostImage:
        self._validate_bucket(event)
        environment, upload_id = self._extract_staging_identity(event.key)
        try:
            self._validate_size(event)
            source = self._download(event)
            original, thumbnail, metadata = self._decode_and_transform(source)

            original_key = self._destination_key(environment, "original", upload_id)
            thumbnail_key = self._destination_key(environment, "thumbnail", upload_id)

            self._upload(event.bucket, original_key, original)
            self._upload(event.bucket, thumbnail_key, thumbnail)
        except RejectedPostImageError as exception:
            self._report_failure(environment, upload_id, exception.reason)
            self._discard_staging(event)
            raise

        metadata["byteSize"] = len(original)
        try:
            self._callback_client.complete(environment, upload_id, metadata)
        except PermanentCallbackError:
            self._abandon_completed_image(environment, upload_id, event)
            raise
        self._s3_client.delete_object(Bucket=event.bucket, Key=event.key)

        return ProcessedPostImage(
            original_key=original_key,
            thumbnail_key=thumbnail_key,
        )

    def _report_failure(self, environment: str, upload_id: str, reason: str) -> None:
        try:
            self._callback_client.failed(environment, upload_id, {"reason": reason})
        except PermanentCallbackError:
            LOGGER.error(
                "failed callback permanently refused for %s",
                upload_id,
            )

    def _abandon_completed_image(
        self,
        environment: str,
        upload_id: str,
        event: S3ObjectCreated,
    ) -> None:
        """
        완료 콜백이 4xx로 영구 거부되면 재시도해도 달라지지 않는다. 그대로 두면 백엔드는 업로드를 ISSUED로
        믿어 게시물이 VALIDATING에 영원히 갇히므로, 실패 콜백으로 상태를 닫아 사용자가 다시 올릴 수 있게 한다.
        원본 staging은 남겨 수동 복구 여지를 둔다.
        """
        LOGGER.error(
            json.dumps(
                {
                    "event": "post_image_complete_callback_refused",
                    "uploadId": upload_id,
                    "bucket": event.bucket,
                    "key": event.key,
                }
            )
        )
        self._report_failure(environment, upload_id, "PROCESSING_ERROR")

    def _discard_staging(self, event: S3ObjectCreated) -> None:
        """
        거절된 원본은 EXIF를 제거하기 전 사용자 파일이라 위치·촬영 시각·기종이 그대로 남아 있다. 아무도
        참조하지 않는 개인정보를 버킷에 방치하지 않는다.
        """
        try:
            self._s3_client.delete_object(Bucket=event.bucket, Key=event.key)
        except ClientError:
            LOGGER.exception(
                json.dumps(
                    {
                        "event": "rejected_staging_delete_failed",
                        "bucket": event.bucket,
                        "key": event.key,
                    }
                )
            )

    def _validate_bucket(self, event: S3ObjectCreated) -> None:
        if event.bucket != self._settings.expected_bucket:
            raise RejectedImageError("image was uploaded to an unexpected bucket")

    def _extract_staging_identity(self, key: str) -> tuple[str, str]:
        match = self._staging_key_pattern.fullmatch(key)
        if match is None:
            raise RejectedImageError("object key is not a post staging key")
        return match.group("environment"), match.group("upload_id")

    def _validate_size(self, event: S3ObjectCreated) -> None:
        if event.size is not None and event.size > self._settings.post_max_bytes:
            raise RejectedPostImageError(
                "TOO_LARGE",
                "image exceeds the maximum input size",
            )

    def _download(self, event: S3ObjectCreated) -> bytes:
        try:
            response = self._s3_client.get_object(Bucket=event.bucket, Key=event.key)
        except ClientError as exception:
            if exception.response.get("Error", {}).get("Code") in {
                "NoSuchKey",
                "NotFound",
                "404",
            }:
                raise RejectedPostImageError(
                    "MISSING_OBJECT",
                    "staging image no longer exists",
                ) from exception
            raise

        content_length = response.get("ContentLength")
        if (
            content_length is not None
            and content_length > self._settings.post_max_bytes
        ):
            raise RejectedPostImageError(
                "TOO_LARGE",
                "image exceeds the maximum input size",
            )

        body = response["Body"]
        try:
            source = body.read(self._settings.post_max_bytes + 1)
        finally:
            body.close()

        if len(source) > self._settings.post_max_bytes:
            raise RejectedPostImageError(
                "TOO_LARGE",
                "image exceeds the maximum input size",
            )
        return source

    def _decode_and_transform(
        self,
        source: bytes,
    ) -> tuple[bytes, bytes, dict[str, Any]]:
        decoded = self._decode(source)
        try:
            metadata = self._extract_metadata(decoded)
            oriented = ImageOps.exif_transpose(decoded)
            sanitized = self._sanitize(oriented)
            metadata["width"] = sanitized.width
            metadata["height"] = sanitized.height

            original = self._encode_webp(
                sanitized,
                self._settings.post_webp_quality,
            )
            thumbnail_image = sanitized.copy()
            thumbnail_image.thumbnail(
                (
                    self._settings.post_thumbnail_max_size,
                    self._settings.post_thumbnail_max_size,
                ),
                Image.Resampling.LANCZOS,
            )
            thumbnail = self._encode_webp(
                thumbnail_image,
                self._settings.post_thumbnail_webp_quality,
            )
        finally:
            decoded.close()

        return original, thumbnail, metadata

    def _decode(self, source: bytes) -> Image.Image:
        try:
            with warnings.catch_warnings():
                warnings.simplefilter("error", Image.DecompressionBombWarning)
                Image.MAX_IMAGE_PIXELS = self._settings.post_max_pixels

                with Image.open(io.BytesIO(source)) as candidate:
                    image_format = candidate.format
                    frame_count = getattr(candidate, "n_frames", 1)
                    candidate.verify()

                if image_format != "WEBP":
                    raise RejectedPostImageError(
                        "UNSUPPORTED_FORMAT",
                        "post image is not a WebP",
                    )
                if frame_count > 1:
                    raise RejectedPostImageError(
                        "ANIMATED_IMAGE",
                        "animated post images are not supported",
                    )

                decoded = Image.open(io.BytesIO(source))
                decoded.load()
                return decoded
        except RejectedPostImageError:
            raise
        except (
            Image.DecompressionBombError,
            Image.DecompressionBombWarning,
        ) as exception:
            raise RejectedPostImageError(
                "TOO_MANY_PIXELS",
                "post image exceeds the decoding pixel limit",
            ) from exception
        except (
            UnidentifiedImageError,
            OSError,
            SyntaxError,
            ValueError,
        ) as exception:
            raise RejectedPostImageError(
                "UNSUPPORTED_FORMAT",
                "post image cannot be decoded safely",
            ) from exception

    def _extract_metadata(self, image: Image.Image) -> dict[str, Any]:
        exif = image.getexif()

        return {
            "location": _location(exif),
            "capturedAt": _captured_at(exif),
            "metaAttributes": self._meta_attributes(exif),
        }

    def _meta_attributes(self, exif: Image.Exif) -> dict[str, Any]:
        attributes: dict[str, Any] = {}
        for tag, value in {**dict(exif), **_exif_ifd(exif)}.items():
            if tag in EXCLUDED_META_TAGS:
                continue
            serialized = _serializable(value)
            if serialized is None:
                continue
            attributes[TAGS.get(tag, str(tag))] = serialized

        return self._truncate(attributes)

    def _truncate(self, attributes: dict[str, Any]) -> dict[str, Any]:
        """
        한도는 콜백이 실제로 보내는 바이트로 재야 한다. 잘림 표시를 검사 뒤에 얹으면 그만큼 한도를
        넘기므로 자리를 미리 확보해 둔다.
        """
        limit = self._settings.post_metadata_max_bytes - _TRUNCATED_MARKER_BYTES
        truncated: dict[str, Any] = {}
        for key, value in sorted(attributes.items()):
            candidate = {**truncated, key: value}
            if _encoded_size(candidate) > limit:
                truncated["_truncated"] = True
                return truncated
            truncated = candidate

        return truncated

    @staticmethod
    def _sanitize(image: Image.Image) -> Image.Image:
        has_alpha = image.mode in {"RGBA", "LA"} or "transparency" in image.info
        return image.convert("RGBA" if has_alpha else "RGB")

    @staticmethod
    def _encode_webp(image: Image.Image, quality: int) -> bytes:
        output = io.BytesIO()
        image.save(output, format="WEBP", quality=quality, method=4)
        return output.getvalue()

    def _destination_key(
        self,
        environment: str,
        variant: str,
        upload_id: str,
    ) -> str:
        return (
            f"{self._settings.root_prefix}/posts/"
            f"{environment}/{variant}/{upload_id}.webp"
        )

    def _upload(self, bucket: str, key: str, body: bytes) -> None:
        self._s3_client.put_object(
            Bucket=bucket,
            Key=key,
            Body=body,
            ContentType=WEBP_CONTENT_TYPE,
            CacheControl=self._settings.post_cache_control,
        )


def _encoded_size(attributes: dict[str, Any]) -> int:
    return len(json.dumps(attributes, separators=(",", ":")).encode("ascii"))


def _serializable(value: Any) -> Any:
    if isinstance(value, (str, int, float, bool)):
        return value
    if isinstance(value, bytes):
        return None
    return str(value)


def _exif_ifd(exif: Image.Exif) -> dict[int, Any]:
    """
    DateTimeOriginal을 비롯한 촬영 정보는 EXIF 규격상 IFD0이 아니라 Exif sub-IFD에 들어간다. 최상위만
    훑으면 실제 카메라 사진에서 촬영 시각과 노출 정보를 통째로 놓친다.
    """
    try:
        return dict(exif.get_ifd(EXIF_IFD_TAG))
    except (KeyError, OSError, ValueError):
        return {}


def _captured_at(exif: Image.Exif) -> str | None:
    tags = {**dict(exif), **_exif_ifd(exif)}
    captured = tags.get(DATE_TIME_ORIGINAL_TAG)
    if not isinstance(captured, str) or not captured.strip():
        return None

    date, _, clock = captured.strip().partition(" ")
    if not clock:
        return None
    timestamp = f"{date.replace(':', '-')}T{clock}"

    offset = tags.get(OFFSET_TIME_ORIGINAL_TAG)
    if isinstance(offset, str) and offset.strip():
        return timestamp + offset.strip()
    return timestamp


def _location(exif: Image.Exif) -> dict[str, float] | None:
    try:
        gps = exif.get_ifd(GPS_IFD_TAG)
    except (KeyError, OSError, ValueError):
        return None
    if not gps:
        return None

    latitude = _coordinate(
        gps.get(GPS_LATITUDE),
        gps.get(GPS_LATITUDE_REF),
        "S",
        MAX_LATITUDE,
    )
    longitude = _coordinate(
        gps.get(GPS_LONGITUDE),
        gps.get(GPS_LONGITUDE_REF),
        "W",
        MAX_LONGITUDE,
    )
    if latitude is None or longitude is None:
        return None

    return {"latitude": latitude, "longitude": longitude}


def _coordinate(
    value: Any,
    reference: Any,
    negative_reference: str,
    limit: float,
) -> float | None:
    """
    조작되거나 손상된 GPS 태그는 좌표가 없는 것으로 다룬다. 여기서 예외가 새어 나가면 실패 콜백조차
    보내지 못한 채 SQS가 같은 메시지를 계속 재전달한다. 3-rational이 아닌 값(스칼라 등)은 언패킹에서
    걸러지므로 길이를 따로 세지 않는다.
    """
    try:
        degrees, minutes, seconds = (float(part) for part in value)
    except (TypeError, ValueError, ZeroDivisionError):
        return None

    decimal = degrees + minutes / 60 + seconds / 3600
    if not math.isfinite(decimal) or abs(decimal) > limit:
        return None
    if isinstance(reference, str) and reference.strip().upper() == negative_reference:
        return -decimal
    return decimal
