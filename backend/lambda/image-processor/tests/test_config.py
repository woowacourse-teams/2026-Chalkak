import os
import unittest
from unittest.mock import patch

from image_processor.config import Settings


VALID_ENVIRONMENT = {
    "S3_BUCKET": "chalkak-test-bucket",
    "S3_PREFIX": "chalkak-test",
    "SIGNATURE_MAX_BYTES": "2048",
    "SIGNATURE_MAX_PIXELS": "3000000",
    "SIGNATURE_THUMBNAIL_MAX_SIZE": "256",
    "DEV_BACKEND_IMAGE_PROCESSING_API_BASE_URL": "https://dev-api.test.chalkak/internal/v1",
    "PROD_BACKEND_IMAGE_PROCESSING_API_BASE_URL": "https://api.test.chalkak/internal/v1",
    "POST_MAX_BYTES": "5242880",
    "POST_MAX_PIXELS": "25000000",
    "POST_THUMBNAIL_MAX_SIZE": "1080",
    "POST_WEBP_QUALITY": "85",
    "POST_THUMBNAIL_WEBP_QUALITY": "80",
    "POST_METADATA_MAX_BYTES": "8192",
    "IMAGE_PROCESSING_API_SECRET": "test-callback-secret-with-enough-length",
    "IMAGE_PROCESSING_API_TIMEOUT_SECONDS": "3",
}


def environment_without(name: str) -> dict[str, str]:
    return {key: value for key, value in VALID_ENVIRONMENT.items() if key != name}


def environment_with(name: str, value: str) -> dict[str, str]:
    return {**VALID_ENVIRONMENT, name: value}



class SettingsTest(unittest.TestCase):
    def test_repr_hides_image_processing_api_secret(self) -> None:
        settings = Settings(
            expected_bucket="bucket",
            root_prefix="chalkak",
            max_input_bytes=1,
            max_image_pixels=1,
            thumbnail_max_size=1,
            image_processing_api_secret="super-secret-value-that-must-not-leak",
        )

        self.assertNotIn("super-secret-value-that-must-not-leak", repr(settings))

    def test_from_environment_uses_backend_s3_variable_names(self) -> None:
        environment = VALID_ENVIRONMENT

        with patch.dict(os.environ, environment, clear=True):
            settings = Settings.from_environment()

        self.assertEqual("chalkak-test-bucket", settings.expected_bucket)
        self.assertEqual("chalkak-test", settings.root_prefix)
        self.assertEqual(2048, settings.max_input_bytes)
        self.assertEqual(3_000_000, settings.max_image_pixels)
        self.assertEqual(256, settings.thumbnail_max_size)
        self.assertEqual(
            "https://dev-api.test.chalkak/internal/v1",
            settings.dev_backend_image_processing_api_base_url,
        )
        self.assertEqual(
            "https://api.test.chalkak/internal/v1",
            settings.prod_backend_image_processing_api_base_url,
        )
        self.assertEqual(
            "test-callback-secret-with-enough-length",
            settings.image_processing_api_secret,
        )
        self.assertEqual(3.0, settings.image_processing_api_timeout_seconds)
        self.assertEqual(5_242_880, settings.post_max_bytes)
        self.assertEqual(25_000_000, settings.post_max_pixels)
        self.assertEqual(1080, settings.post_thumbnail_max_size)
        self.assertEqual(85, settings.post_webp_quality)
        self.assertEqual(80, settings.post_thumbnail_webp_quality)
        self.assertEqual(8192, settings.post_metadata_max_bytes)
    def test_from_environment_rejects_non_positive_limit(self) -> None:
        with patch.dict(os.environ, {"SIGNATURE_MAX_BYTES": "0"}, clear=True):
            with self.assertRaisesRegex(
                ValueError,
                "SIGNATURE_MAX_BYTES must be greater than zero",
            ):
                Settings.from_environment()


    def test_from_environment_requires_every_api_base_url(self) -> None:
        for name in (
            "DEV_BACKEND_IMAGE_PROCESSING_API_BASE_URL",
            "PROD_BACKEND_IMAGE_PROCESSING_API_BASE_URL",
        ):
            with self.subTest(name=name):
                with patch.dict(os.environ, environment_without(name), clear=True):
                    with self.assertRaisesRegex(ValueError, f"{name} must not be blank"):
                        Settings.from_environment()

    def test_from_environment_rejects_short_api_secret(self) -> None:
        environment = environment_with("IMAGE_PROCESSING_API_SECRET", "a" * 31)

        with patch.dict(os.environ, environment, clear=True):
            with self.assertRaisesRegex(ValueError, "at least 32 characters"):
                Settings.from_environment()

    def test_from_environment_rejects_non_numeric_limit(self) -> None:
        environment = environment_with("POST_MAX_BYTES", "not-a-number")

        with patch.dict(os.environ, environment, clear=True):
            with self.assertRaisesRegex(ValueError, "POST_MAX_BYTES must be an integer"):
                Settings.from_environment()

    def test_from_environment_rejects_non_numeric_timeout(self) -> None:
        environment = environment_with("IMAGE_PROCESSING_API_TIMEOUT_SECONDS", "soon")

        with patch.dict(os.environ, environment, clear=True):
            with self.assertRaisesRegex(
                ValueError,
                "IMAGE_PROCESSING_API_TIMEOUT_SECONDS must be a number",
            ):
                Settings.from_environment()

    def test_from_environment_rejects_blank_root_prefix(self) -> None:
        environment = environment_with("S3_PREFIX", "/")

        with patch.dict(os.environ, environment, clear=True):
            with self.assertRaisesRegex(ValueError, "S3_PREFIX must not be blank"):
                Settings.from_environment()


if __name__ == "__main__":
    unittest.main()
