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
        )


def _positive_int(name: str, default: int) -> int:
    value = int(os.environ.get(name, str(default)))
    if value <= 0:
        raise ValueError(f"{name} must be greater than zero")
    return value
