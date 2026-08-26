import hashlib
import hmac
import json
import unittest
from unittest.mock import MagicMock, patch

from urllib.error import HTTPError

from image_processor.callback import ProcessingCallbackClient
from image_processor.errors import PermanentCallbackError

UPLOAD_ID = "0198d999-ff00-7000-8000-000000000001"
SECRET = "test-callback-secret-with-enough-length"


class SignatureProcessingCallbackClientTest(unittest.TestCase):
    def setUp(self) -> None:
        self.client = ProcessingCallbackClient(
            kind="signature-processing",
            base_urls={
                "dev": "https://dev-api.test.chalkak/internal/v1/signature-processing",
                "prod": "https://api.test.chalkak/internal/v1/signature-processing",
            },
            secret=SECRET,
            timeout_seconds=3.0,
        )

    def _http_error(self, status: int) -> HTTPError:
        return HTTPError(
            url="https://dev-api.test.chalkak/internal/v1/signature-processing",
            code=status,
            msg="error",
            hdrs=None,
            fp=None,
        )

    @patch("image_processor.callback.request.urlopen")
    def test_complete_raises_permanent_error_for_non_retryable_status(
        self, urlopen
    ) -> None:
        for status in (400, 404, 405, 413, 414, 415):
            with self.subTest(status=status):
                urlopen.side_effect = self._http_error(status)

                with self.assertRaises(PermanentCallbackError):
                    self.client.complete("dev", UPLOAD_ID)

    @patch("image_processor.callback.request.urlopen")
    def test_complete_propagates_retryable_status(self, urlopen) -> None:
        for status in (401, 403, 408, 429, 500, 503):
            with self.subTest(status=status):
                urlopen.side_effect = self._http_error(status)

                with self.assertRaises(HTTPError):
                    self.client.complete("dev", UPLOAD_ID)


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


class PostImageProcessingCallbackClientTest(unittest.TestCase):
    def setUp(self) -> None:
        self.client = ProcessingCallbackClient(
            kind="post-image-processing",
            base_urls={
                "dev": "https://dev-api.test.chalkak/internal/v1/post-image-processing",
                "prod": "https://api.test.chalkak/internal/v1/post-image-processing",
            },
            secret=SECRET,
            timeout_seconds=3.0,
        )

    @patch("image_processor.callback.time.time", return_value=1_787_562_000)
    @patch("image_processor.callback.request.urlopen")
    def test_complete_signs_json_body_it_actually_sends(self, urlopen, _time) -> None:
        response = MagicMock()
        response.status = 204
        urlopen.return_value.__enter__.return_value = response
        body = {
            "width": 4032,
            "height": 3024,
            "byteSize": 812345,
            "location": None,
            "capturedAt": None,
            "metaAttributes": {"Model": "iPhone 15 Pro"},
        }

        self.client.complete("dev", UPLOAD_ID, body)

        request_value = urlopen.call_args.args[0]
        timestamp = "1787562000"
        path = f"/internal/v1/post-image-processing/{UPLOAD_ID}/complete"
        body_hash = hashlib.sha256(request_value.data).hexdigest()
        expected_signature = "v1=" + hmac.new(
            SECRET.encode(),
            f"{timestamp}\nPOST\n{path}\n{body_hash}".encode(),
            hashlib.sha256,
        ).hexdigest()
        self.assertEqual(
            expected_signature,
            request_value.headers["X-chalkak-callback-signature"],
        )
        self.assertEqual(
            "application/json; charset=utf-8",
            request_value.headers["Content-type"],
        )
        self.assertEqual(body, json.loads(request_value.data))

    @patch("image_processor.callback.time.time", return_value=1_787_562_000)
    @patch("image_processor.callback.request.urlopen")
    def test_complete_escapes_non_ascii_body(self, urlopen, _time) -> None:
        response = MagicMock()
        response.status = 204
        urlopen.return_value.__enter__.return_value = response
        body = {"metaAttributes": {"LensModel": "표준 줌 렌즈"}}

        self.client.complete("dev", UPLOAD_ID, body)

        sent = urlopen.call_args.args[0].data
        sent.decode("ascii")
        self.assertEqual(body, json.loads(sent))

    @patch("image_processor.callback.time.time", return_value=1_787_562_000)
    @patch("image_processor.callback.request.urlopen")
    def test_failed_posts_reason_body(self, urlopen, _time) -> None:
        response = MagicMock()
        response.status = 204
        urlopen.return_value.__enter__.return_value = response

        self.client.failed("prod", UPLOAD_ID, {"reason": "UNSUPPORTED_FORMAT"})

        request_value = urlopen.call_args.args[0]
        self.assertEqual(
            f"https://api.test.chalkak/internal/v1/post-image-processing/{UPLOAD_ID}/failed",
            request_value.full_url,
        )
        self.assertEqual(
            {"reason": "UNSUPPORTED_FORMAT"},
            json.loads(request_value.data),
        )


if __name__ == "__main__":
    unittest.main()
