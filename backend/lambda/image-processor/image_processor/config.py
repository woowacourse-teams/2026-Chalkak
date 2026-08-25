import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    expected_bucket: str
    root_prefix: str
    max_input_bytes: int
    max_image_pixels: int
    thumbnail_max_size: int
    cache_control: str
    post_max_bytes: int = 5_242_880
    post_max_pixels: int = 25_000_000
    post_thumbnail_max_size: int = 1080
    post_webp_quality: int = 85
    post_thumbnail_webp_quality: int = 80
    post_metadata_max_bytes: int = 8192
    post_cache_control: str = "public, max-age=86400"
    dev_callback_base_url: str = ""
    prod_callback_base_url: str = ""
    dev_post_callback_base_url: str = ""
    prod_post_callback_base_url: str = ""
    callback_secret: str = ""
    callback_timeout_seconds: float = 3.0

    @classmethod
    def from_environment(cls) -> "Settings":
        return cls(
            expected_bucket=os.environ.get(
                "S3_BUCKET",
                "techcourse-project-2026",
            ),
            root_prefix=os.environ.get("S3_PREFIX", "chalkak").strip("/"),
            max_input_bytes=_positive_int("SIGNATURE_MAX_BYTES", 1_048_576),
            max_image_pixels=_positive_int(
                "SIGNATURE_MAX_PIXELS",
                25_000_000,
            ),
            thumbnail_max_size=_positive_int(
                "SIGNATURE_THUMBNAIL_MAX_SIZE",
                512,
            ),
            cache_control=os.environ.get(
                "SIGNATURE_CACHE_CONTROL",
                "public, max-age=86400",
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
            post_cache_control=os.environ.get(
                "POST_CACHE_CONTROL",
                "public, max-age=86400",
            ),
            dev_callback_base_url=_required(
                "DEV_BACKEND_CALLBACK_URL"
            ).rstrip("/"),
            prod_callback_base_url=_required(
                "PROD_BACKEND_CALLBACK_URL"
            ).rstrip("/"),
            dev_post_callback_base_url=_required(
                "DEV_BACKEND_POST_CALLBACK_URL"
            ).rstrip("/"),
            prod_post_callback_base_url=_required(
                "PROD_BACKEND_POST_CALLBACK_URL"
            ).rstrip("/"),
            callback_secret=_callback_secret(),
            callback_timeout_seconds=_positive_float(
                "BACKEND_CALLBACK_TIMEOUT_SECONDS",
                3.0,
            ),
        )


def _positive_int(name: str, default: int) -> int:
    value = int(os.environ.get(name, str(default)))
    if value <= 0:
        raise ValueError(f"{name} must be greater than zero")
    return value


def _positive_float(name: str, default: float) -> float:
    value = float(os.environ.get(name, str(default)))
    if value <= 0:
        raise ValueError(f"{name} must be greater than zero")
    return value


def _required(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise ValueError(f"{name} must not be blank")
    return value


def _callback_secret() -> str:
    secret = _required("IMAGE_PROCESSOR_CALLBACK_SECRET")
    if len(secret) < 32:
        raise ValueError("IMAGE_PROCESSOR_CALLBACK_SECRET must be at least 32 characters")
    return secret
