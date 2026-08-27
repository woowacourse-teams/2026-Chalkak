import os
from dataclasses import dataclass, field


@dataclass(frozen=True)
class Settings:
    expected_bucket: str
    root_prefix: str
    max_input_bytes: int
    max_image_pixels: int
    thumbnail_max_size: int
    post_max_bytes: int = 5_242_880
    post_max_pixels: int = 25_000_000
    post_thumbnail_max_size: int = 1080
    post_webp_quality: int = 85
    post_thumbnail_webp_quality: int = 80
    post_metadata_max_bytes: int = 8192
    dev_backend_image_processing_api_base_url: str = ""
    prod_backend_image_processing_api_base_url: str = ""
    # dataclass가 자동 생성하는 __repr__은 모든 필드를 값째로 찍는다. 디버그 로그 한 줄이나
    # 이 객체를 담은 traceback 하나로 HMAC 시크릿이 CloudWatch에 평문으로 남지 않게 제외한다.
    image_processing_api_secret: str = field(default="", repr=False)
    image_processing_api_timeout_seconds: float = 3.0

    @classmethod
    def from_environment(cls) -> "Settings":
        return cls(
            expected_bucket=os.environ.get(
                "S3_BUCKET",
                "techcourse-project-2026",
            ),
            root_prefix=_root_prefix(),
            max_input_bytes=_positive_int("SIGNATURE_MAX_BYTES", 1_048_576),
            max_image_pixels=_positive_int(
                "SIGNATURE_MAX_PIXELS",
                25_000_000,
            ),
            thumbnail_max_size=_positive_int(
                "SIGNATURE_THUMBNAIL_MAX_SIZE",
                512,
            ),
            post_max_bytes=_positive_int("POST_MAX_BYTES", 5_242_880),
            post_max_pixels=_positive_int("POST_MAX_PIXELS", 25_000_000),
            post_thumbnail_max_size=_positive_int(
                "POST_THUMBNAIL_MAX_SIZE",
                1080,
            ),
            post_webp_quality=_positive_int("POST_WEBP_QUALITY", 85),
            post_thumbnail_webp_quality=_positive_int(
                "POST_THUMBNAIL_WEBP_QUALITY",
                80,
            ),
            post_metadata_max_bytes=_positive_int(
                "POST_METADATA_MAX_BYTES",
                8192,
            ),
            dev_backend_image_processing_api_base_url=_required(
                "DEV_BACKEND_IMAGE_PROCESSING_API_BASE_URL"
            ).rstrip("/"),
            prod_backend_image_processing_api_base_url=_required(
                "PROD_BACKEND_IMAGE_PROCESSING_API_BASE_URL"
            ).rstrip("/"),
            image_processing_api_secret=_image_processing_api_secret(),
            image_processing_api_timeout_seconds=_positive_float(
                "IMAGE_PROCESSING_API_TIMEOUT_SECONDS",
                3.0,
            ),
        )


def _positive_int(name: str, default: int) -> int:
    raw = os.environ.get(name, str(default))
    try:
        value = int(raw)
    except ValueError as exception:
        raise ValueError(f"{name} must be an integer") from exception
    if value <= 0:
        raise ValueError(f"{name} must be greater than zero")
    return value


def _positive_float(name: str, default: float) -> float:
    raw = os.environ.get(name, str(default))
    try:
        value = float(raw)
    except ValueError as exception:
        raise ValueError(f"{name} must be a number") from exception
    if value <= 0:
        raise ValueError(f"{name} must be greater than zero")
    return value


def _root_prefix() -> str:
    """
    빈 prefix는 staging 키가 슬래시로 시작하게 만들어 어떤 객체와도 매칭되지 않는다. 숫자 설정과 달리
    조용히 통과하던 자리라 기동 시점에 막는다.
    """
    prefix = os.environ.get("S3_PREFIX", "chalkak").strip("/")
    if not prefix:
        raise ValueError("S3_PREFIX must not be blank")
    return prefix


def _required(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise ValueError(f"{name} must not be blank")
    return value


def _image_processing_api_secret() -> str:
    secret = _required("IMAGE_PROCESSING_API_SECRET")
    if len(secret) < 32:
        raise ValueError("IMAGE_PROCESSING_API_SECRET must be at least 32 characters")
    return secret
