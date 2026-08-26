import io
import unittest
from unittest.mock import Mock

from PIL import Image

from image_processor.config import Settings
from image_processor.errors import PermanentCallbackError, RejectedImageError
from image_processor.events import S3ObjectCreated
from image_processor.signature import SignatureImageProcessor

BUCKET = "test-bucket"
UPLOAD_ID = "0198d999-ff00-7000-8000-000000000001"
ENVIRONMENT = "dev"
STAGING_KEY = f"chalkak/staging/{ENVIRONMENT}/signatures/{UPLOAD_ID}.png"


class StreamingBodyStub(io.BytesIO):
    pass


class SignatureImageProcessorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.s3_client = Mock()
        self.settings = Settings(
            expected_bucket=BUCKET,
            root_prefix="chalkak",
            max_input_bytes=1_048_576,
            max_image_pixels=25_000_000,
            thumbnail_max_size=64,
            cache_control="public, max-age=86400",
        )
        self.callback_client = Mock()
        self.processor = SignatureImageProcessor(
            self.s3_client,
            self.settings,
            self.callback_client,
        )

    def test_process_reencodes_original_creates_thumbnail_and_deletes_staging(
        self,
    ) -> None:
        source = png_image(size=(200, 100), mode="RGBA")
        self.s3_client.get_object.return_value = {
            "ContentLength": len(source),
            "Body": StreamingBodyStub(source),
        }

        result = self.processor.process(created_event(len(source)))

        self.assertEqual(
            f"chalkak/signatures/dev/original/{UPLOAD_ID}.png",
            result.original_key,
        )
        self.assertEqual(
            f"chalkak/signatures/dev/thumbnail/{UPLOAD_ID}.png",
            result.thumbnail_key,
        )
        self.assertEqual(2, self.s3_client.put_object.call_count)

        original_put = self.s3_client.put_object.call_args_list[0].kwargs
        thumbnail_put = self.s3_client.put_object.call_args_list[1].kwargs
        self.assertEqual("image/png", original_put["ContentType"])
        self.assertEqual((200, 100), image_size(original_put["Body"]))
        self.assertEqual((64, 32), image_size(thumbnail_put["Body"]))
        self.callback_client.complete.assert_called_once_with(ENVIRONMENT, UPLOAD_ID)
        self.s3_client.delete_object.assert_called_once_with(
            Bucket=BUCKET,
            Key=STAGING_KEY,
        )

    def test_process_preserves_transparency(self) -> None:
        source = png_image(size=(10, 10), mode="RGBA")
        self.s3_client.get_object.return_value = {
            "ContentLength": len(source),
            "Body": StreamingBodyStub(source),
        }

        self.processor.process(created_event(len(source)))

        original = self.s3_client.put_object.call_args_list[0].kwargs["Body"]
        with Image.open(io.BytesIO(original)) as image:
            self.assertEqual("RGBA", image.mode)

    def test_process_routes_prod_result_and_callback_to_prod(self) -> None:
        source = png_image()
        prod_staging_key = f"chalkak/staging/prod/signatures/{UPLOAD_ID}.png"
        self.s3_client.get_object.return_value = {
            "ContentLength": len(source),
            "Body": StreamingBodyStub(source),
        }

        result = self.processor.process(
            S3ObjectCreated(bucket=BUCKET, key=prod_staging_key, size=len(source))
        )

        self.assertEqual(
            f"chalkak/signatures/prod/original/{UPLOAD_ID}.png",
            result.original_key,
        )
        self.assertEqual(
            f"chalkak/signatures/prod/thumbnail/{UPLOAD_ID}.png",
            result.thumbnail_key,
        )
        self.callback_client.complete.assert_called_once_with("prod", UPLOAD_ID)

    def test_process_deletes_staging_after_rejection_callback_succeeds(self) -> None:
        source = jpeg_image()
        self.s3_client.get_object.return_value = {
            "ContentLength": len(source),
            "Body": StreamingBodyStub(source),
        }

        with self.assertRaisesRegex(RejectedImageError, "not a PNG"):
            self.processor.process(created_event(len(source)))

        self.callback_client.failed.assert_called_once_with(ENVIRONMENT, UPLOAD_ID)
        self.s3_client.put_object.assert_not_called()
        self.s3_client.delete_object.assert_called_once_with(
            Bucket=BUCKET,
            Key=STAGING_KEY,
        )

    def test_process_keeps_rejection_when_failed_callback_is_permanently_refused(
        self,
    ) -> None:
        source = jpeg_image()
        self.s3_client.get_object.return_value = {
            "ContentLength": len(source),
            "Body": StreamingBodyStub(source),
        }
        self.callback_client.failed.side_effect = PermanentCallbackError(
            "backend callback rejected with HTTP 400"
        )

        # 실패 콜백이 영구 거부돼도 원래 거부 사유가 살아남아야 재처리 루프에 빠지지 않는다.
        with self.assertLogs("image_processor.signature", level="ERROR") as logs:
            with self.assertRaisesRegex(RejectedImageError, "not a PNG"):
                self.processor.process(created_event(len(source)))

        self.s3_client.delete_object.assert_not_called()
        self.assertEqual(
            [
                "ERROR:image_processor.signature:failed callback permanently "
                f"refused for {UPLOAD_ID} (bucket={BUCKET}, key={STAGING_KEY}): "
                "backend callback rejected with HTTP 400"
            ],
            logs.output,
        )

    def test_process_rejects_size_from_event_before_download(self) -> None:
        event = created_event(self.settings.max_input_bytes + 1)

        with self.assertRaisesRegex(RejectedImageError, "maximum input size"):
            self.processor.process(event)

        self.callback_client.failed.assert_called_once_with(ENVIRONMENT, UPLOAD_ID)
        self.s3_client.get_object.assert_not_called()

    def test_process_rejects_unexpected_staging_key(self) -> None:
        event = S3ObjectCreated(
            bucket=BUCKET,
            key=f"chalkak/staging/dev/posts/{UPLOAD_ID}.png",
            size=100,
        )

        with self.assertRaisesRegex(RejectedImageError, "signature staging key"):
            self.processor.process(event)

        self.callback_client.failed.assert_not_called()
        self.s3_client.get_object.assert_not_called()

    def test_process_rejects_unsupported_environment(self) -> None:
        event = S3ObjectCreated(
            bucket=BUCKET,
            key=f"chalkak/staging/test/signatures/{UPLOAD_ID}.png",
            size=100,
        )

        with self.assertRaisesRegex(RejectedImageError, "signature staging key"):
            self.processor.process(event)

        self.callback_client.failed.assert_not_called()
        self.s3_client.get_object.assert_not_called()

    def test_process_does_not_delete_staging_when_thumbnail_upload_fails(self) -> None:
        source = png_image()
        self.s3_client.get_object.return_value = {
            "ContentLength": len(source),
            "Body": StreamingBodyStub(source),
        }
        self.s3_client.put_object.side_effect = [None, TimeoutError("S3 timeout")]

        with self.assertRaises(TimeoutError):
            self.processor.process(created_event(len(source)))

        self.callback_client.complete.assert_not_called()
        self.s3_client.delete_object.assert_not_called()

    def test_process_does_not_delete_staging_when_complete_callback_fails(
        self,
    ) -> None:
        source = png_image()
        self.s3_client.get_object.return_value = {
            "ContentLength": len(source),
            "Body": StreamingBodyStub(source),
        }
        self.callback_client.complete.side_effect = TimeoutError("callback timeout")

        with self.assertRaises(TimeoutError):
            self.processor.process(created_event(len(source)))

        self.s3_client.delete_object.assert_not_called()

    def test_process_retries_when_failed_callback_cannot_be_delivered(self) -> None:
        source = jpeg_image()
        self.s3_client.get_object.return_value = {
            "ContentLength": len(source),
            "Body": StreamingBodyStub(source),
        }
        self.callback_client.failed.side_effect = TimeoutError("callback timeout")

        with self.assertRaises(TimeoutError):
            self.processor.process(created_event(len(source)))

        self.s3_client.delete_object.assert_not_called()


def created_event(size: int) -> S3ObjectCreated:
    return S3ObjectCreated(bucket=BUCKET, key=STAGING_KEY, size=size)


def png_image(size: tuple[int, int] = (100, 50), mode: str = "RGB") -> bytes:
    color = (255, 0, 0, 100) if mode == "RGBA" else (255, 0, 0)
    image = Image.new(mode, size, color)
    output = io.BytesIO()
    image.save(output, format="PNG")
    return output.getvalue()


def jpeg_image() -> bytes:
    image = Image.new("RGB", (100, 50), (255, 0, 0))
    output = io.BytesIO()
    image.save(output, format="JPEG")
    return output.getvalue()


def image_size(value: bytes) -> tuple[int, int]:
    with Image.open(io.BytesIO(value)) as image:
        return image.size


if __name__ == "__main__":
    unittest.main()
