import hashlib
import hmac
import unittest
from unittest.mock import MagicMock, patch

from image_processor.callback import SignatureProcessingCallbackClient

UPLOAD_ID = "0198d999-ff00-7000-8000-000000000001"
SECRET = "test-callback-secret-with-enough-length"


class SignatureProcessingCallbackClientTest(unittest.TestCase):
    def setUp(self) -> None:
        self.client = SignatureProcessingCallbackClient(
            base_urls={
                "dev": "https://dev-api.test.chalkak/internal/v1/signature-processing",
                "prod": "https://api.test.chalkak/internal/v1/signature-processing",
            },
            secret=SECRET,
            timeout_seconds=3.0,
        )

    @patch("image_processor.callback.time.time", return_value=1_787_562_000)
    @patch("image_processor.callback.request.urlopen")
    def test_complete_posts_signed_callback(self, urlopen, _time) -> None:
        response = MagicMock()
        response.status = 204
        urlopen.return_value.__enter__.return_value = response

        self.client.complete("dev", UPLOAD_ID)

        request_value = urlopen.call_args.args[0]
        timestamp = "1787562000"
        path = f"/internal/v1/signature-processing/{UPLOAD_ID}/complete"
        body_hash = hashlib.sha256(b"").hexdigest()
        expected_signature = "v1=" + hmac.new(
            SECRET.encode(),
            f"{timestamp}\nPOST\n{path}\n{body_hash}".encode(),
            hashlib.sha256,
        ).hexdigest()
        self.assertEqual(
            f"https://dev-api.test.chalkak/internal/v1/signature-processing/{UPLOAD_ID}/complete",
            request_value.full_url,
        )
        self.assertEqual(
            timestamp,
            request_value.headers["X-chalkak-callback-timestamp"],
        )
        self.assertEqual(
            expected_signature,
            request_value.headers["X-chalkak-callback-signature"],
        )
        urlopen.assert_called_once_with(request_value, timeout=3.0)

    @patch("image_processor.callback.time.time", return_value=1_787_562_000)
    @patch("image_processor.callback.request.urlopen")
    def test_failed_posts_failed_result(self, urlopen, _time) -> None:
        response = MagicMock()
        response.status = 204
        urlopen.return_value.__enter__.return_value = response

        self.client.failed("prod", UPLOAD_ID)

        self.assertTrue(
            urlopen.call_args.args[0].full_url.endswith(f"/{UPLOAD_ID}/failed")
        )

    def test_callback_rejects_unsupported_environment(self) -> None:
        with self.assertRaisesRegex(ValueError, "unsupported image environment"):
            self.client.complete("local", UPLOAD_ID)


if __name__ == "__main__":
    unittest.main()
