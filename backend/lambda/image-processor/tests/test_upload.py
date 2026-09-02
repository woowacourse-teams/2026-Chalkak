import unittest
from unittest.mock import MagicMock, patch
from urllib.error import HTTPError

from image_processor.upload import PresignedUploadClient


class PresignedUploadClientTest(unittest.TestCase):
    @patch("image_processor.upload.request.urlopen")
    def test_upload_puts_body_with_all_signed_headers(self, urlopen) -> None:
        response = MagicMock()
        response.status = 200
        urlopen.return_value.__enter__.return_value = response
        client = PresignedUploadClient(timeout_seconds=30.0)

        uploaded = client.upload(
            url="https://s3.test/presigned",
            body=b"processed-image",
            content_type="image/webp",
            cache_control="public, max-age=86400",
        )

        self.assertTrue(uploaded)
        request_value = urlopen.call_args.args[0]
        self.assertEqual("PUT", request_value.method)
        self.assertEqual(b"processed-image", request_value.data)
        self.assertEqual("image/webp", request_value.headers["Content-type"])
        self.assertEqual(
            "public, max-age=86400",
            request_value.headers["Cache-control"],
        )
        self.assertEqual("*", request_value.headers["If-none-match"])
        urlopen.assert_called_once_with(request_value, timeout=30.0)

    @patch("image_processor.upload.request.urlopen")
    def test_upload_treats_precondition_failure_as_existing_result(
        self,
        urlopen,
    ) -> None:
        urlopen.side_effect = HTTPError(
            url="https://s3.test/presigned",
            code=412,
            msg="Precondition Failed",
            hdrs=None,
            fp=None,
        )
        client = PresignedUploadClient(timeout_seconds=30.0)

        uploaded = client.upload(
            url="https://s3.test/presigned",
            body=b"processed-image",
            content_type="image/png",
            cache_control="no-cache",
        )

        self.assertFalse(uploaded)

    @patch("image_processor.upload.request.urlopen")
    def test_upload_propagates_other_http_errors(self, urlopen) -> None:
        error = HTTPError(
            url="https://s3.test/presigned",
            code=403,
            msg="Forbidden",
            hdrs=None,
            fp=None,
        )
        urlopen.side_effect = error
        client = PresignedUploadClient(timeout_seconds=30.0)

        with self.assertRaises(HTTPError) as raised:
            client.upload(
                url="https://s3.test/presigned",
                body=b"processed-image",
                content_type="image/png",
                cache_control="no-cache",
            )

        self.assertIs(error, raised.exception)


if __name__ == "__main__":
    unittest.main()
