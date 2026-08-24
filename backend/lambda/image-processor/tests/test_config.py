import os
import unittest
from unittest.mock import patch

from image_processor.config import Settings


class SettingsTest(unittest.TestCase):
    def test_from_environment_uses_backend_s3_variable_names(self) -> None:
        environment = {
            "S3_BUCKET": "chalkak-test-bucket",
            "S3_PREFIX": "chalkak-test",
            "SIGNATURE_MAX_BYTES": "2048",
            "SIGNATURE_MAX_PIXELS": "3000000",
            "SIGNATURE_THUMBNAIL_MAX_SIZE": "256",
            "SIGNATURE_CACHE_CONTROL": "no-cache",
            "DEV_BACKEND_CALLBACK_URL": "https://dev-api.test.chalkak/internal/v1/signature-processing",
            "PROD_BACKEND_CALLBACK_URL": "https://api.test.chalkak/internal/v1/signature-processing",
            "IMAGE_PROCESSOR_CALLBACK_SECRET": "test-callback-secret-with-enough-length",
            "BACKEND_CALLBACK_TIMEOUT_SECONDS": "3",
        }

        with patch.dict(os.environ, environment, clear=True):
            settings = Settings.from_environment()

        self.assertEqual("chalkak-test-bucket", settings.expected_bucket)
        self.assertEqual("chalkak-test", settings.root_prefix)
        self.assertEqual(2048, settings.max_input_bytes)
        self.assertEqual(3_000_000, settings.max_image_pixels)
        self.assertEqual(256, settings.thumbnail_max_size)
        self.assertEqual("no-cache", settings.cache_control)
        self.assertEqual(
            "https://dev-api.test.chalkak/internal/v1/signature-processing",
            settings.dev_callback_base_url,
        )
        self.assertEqual(
            "https://api.test.chalkak/internal/v1/signature-processing",
            settings.prod_callback_base_url,
        )
        self.assertEqual(
            "test-callback-secret-with-enough-length",
            settings.callback_secret,
        )
        self.assertEqual(3.0, settings.callback_timeout_seconds)

    def test_from_environment_rejects_non_positive_limit(self) -> None:
        with patch.dict(os.environ, {"SIGNATURE_MAX_BYTES": "0"}, clear=True):
            with self.assertRaisesRegex(
                ValueError,
                "SIGNATURE_MAX_BYTES must be greater than zero",
            ):
                Settings.from_environment()


if __name__ == "__main__":
    unittest.main()
