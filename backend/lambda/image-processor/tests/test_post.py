import io
import unittest
from unittest.mock import Mock

from PIL import Image

from image_processor.config import Settings
from image_processor.errors import RejectedImageError
from image_processor.events import S3ObjectCreated
from image_processor.post import PostImageProcessor

UPLOAD_ID = "0198d999-ff00-7000-8000-000000000001"
BUCKET = "test-bucket"
STAGING_KEY = f"chalkak/staging/dev/posts/{UPLOAD_ID}.webp"
ORIGINAL_KEY = f"chalkak/posts/dev/original/{UPLOAD_ID}.webp"
THUMBNAIL_KEY = f"chalkak/posts/dev/thumbnail/{UPLOAD_ID}.webp"


def settings() -> Settings:
    return Settings(
        expected_bucket=BUCKET,
        root_prefix="chalkak",
        max_input_bytes=1_048_576,
        max_image_pixels=25_000_000,
        thumbnail_max_size=512,
        cache_control="public, max-age=86400",
        post_max_bytes=5_242_880,
        post_max_pixels=25_000_000,
        post_thumbnail_max_size=1080,
        post_webp_quality=85,
        post_thumbnail_webp_quality=80,
        post_metadata_max_bytes=8192,
        post_cache_control="public, max-age=86400",
        dev_callback_base_url="https://dev.test/internal/v1/signature-processing",
        prod_callback_base_url="https://prod.test/internal/v1/signature-processing",
        dev_post_callback_base_url="https://dev.test/internal/v1/post-image-processing",
        prod_post_callback_base_url="https://prod.test/internal/v1/post-image-processing",
        callback_secret="test-callback-secret-with-enough-length",
        callback_timeout_seconds=3.0,
    )


def webp_bytes(
    size: tuple[int, int] = (40, 30),
    image_format: str = "WEBP",
    exif: bytes | None = None,
) -> bytes:
    image = Image.new("RGB", size, (120, 30, 200))
    output = io.BytesIO()
    if exif is None:
        image.save(output, format=image_format)
        return output.getvalue()
    image.save(output, format=image_format, exif=exif)
    return output.getvalue()


def animated_webp_bytes() -> bytes:
    frames = [Image.new("RGB", (20, 20), color) for color in ((0, 0, 0), (255, 255, 255))]
    output = io.BytesIO()
    frames[0].save(
        output,
        format="WEBP",
        save_all=True,
        append_images=frames[1:],
        duration=100,
    )
    return output.getvalue()


def exif_bytes(tags: dict[int, str]) -> bytes:
    exif = Image.Exif()
    for tag, value in tags.items():
        exif[tag] = value
    return exif.tobytes()


class PostImageProcessorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.s3_client = Mock()
        self.callback_client = Mock()
        self.processor = PostImageProcessor(
            s3_client=self.s3_client,
            settings=settings(),
            callback_client=self.callback_client,
        )

    def given_object(self, body: bytes) -> None:
        stream = io.BytesIO(body)
        self.s3_client.get_object.return_value = {
            "ContentLength": len(body),
            "Body": stream,
        }

    def event(self, size: int | None = 100, key: str = STAGING_KEY) -> S3ObjectCreated:
        return S3ObjectCreated(bucket=BUCKET, key=key, size=size)

    def uploaded(self, key: str) -> dict:
        for call in self.s3_client.put_object.call_args_list:
            if call.kwargs["Key"] == key:
                return call.kwargs
        raise AssertionError(f"{key} was not uploaded")

    def test_process_uploads_original_and_thumbnail_as_webp(self) -> None:
        self.given_object(webp_bytes())

        result = self.processor.process(self.event())

        self.assertEqual(ORIGINAL_KEY, result.original_key)
        self.assertEqual(THUMBNAIL_KEY, result.thumbnail_key)
        original = self.uploaded(ORIGINAL_KEY)
        self.assertEqual(BUCKET, original["Bucket"])
        self.assertEqual("image/webp", original["ContentType"])
        self.assertEqual("public, max-age=86400", original["CacheControl"])
        self.assertEqual("WEBP", Image.open(io.BytesIO(original["Body"])).format)
        thumbnail = self.uploaded(THUMBNAIL_KEY)
        self.assertEqual("WEBP", Image.open(io.BytesIO(thumbnail["Body"])).format)

    def test_process_routes_prod_key_to_prod_destination(self) -> None:
        self.given_object(webp_bytes())

        result = self.processor.process(
            self.event(key=f"chalkak/staging/prod/posts/{UPLOAD_ID}.webp")
        )

        self.assertEqual(f"chalkak/posts/prod/original/{UPLOAD_ID}.webp", result.original_key)
        self.callback_client.complete.assert_called_once()
        self.assertEqual("prod", self.callback_client.complete.call_args.args[0])

    def test_process_deletes_staging_object_after_callback(self) -> None:
        self.given_object(webp_bytes())

        self.processor.process(self.event())

        self.callback_client.complete.assert_called_once()
        self.s3_client.delete_object.assert_called_once_with(
            Bucket=BUCKET, Key=STAGING_KEY
        )

    def test_process_shrinks_thumbnail_to_max_size_keeping_aspect_ratio(self) -> None:
        self.given_object(webp_bytes(size=(2160, 1080)))

        self.processor.process(self.event())

        thumbnail = Image.open(io.BytesIO(self.uploaded(THUMBNAIL_KEY)["Body"]))
        self.assertEqual((1080, 540), thumbnail.size)

    def test_process_keeps_original_resolution(self) -> None:
        self.given_object(webp_bytes(size=(2160, 1080)))

        self.processor.process(self.event())

        original = Image.open(io.BytesIO(self.uploaded(ORIGINAL_KEY)["Body"]))
        self.assertEqual((2160, 1080), original.size)

    def test_process_rejects_key_outside_post_staging(self) -> None:
        with self.assertRaisesRegex(RejectedImageError, "post staging key"):
            self.processor.process(
                self.event(key=f"chalkak/staging/dev/signatures/{UPLOAD_ID}.png")
            )

        self.callback_client.failed.assert_not_called()

    def test_process_rejects_unexpected_bucket(self) -> None:
        event = S3ObjectCreated(bucket="other-bucket", key=STAGING_KEY, size=100)

        with self.assertRaisesRegex(RejectedImageError, "unexpected bucket"):
            self.processor.process(event)

        self.callback_client.failed.assert_not_called()

    def test_process_rejects_event_size_over_limit(self) -> None:
        with self.assertRaises(RejectedImageError):
            self.processor.process(self.event(size=5_242_881))

        self.callback_client.failed.assert_called_once_with(
            "dev", UPLOAD_ID, {"reason": "TOO_LARGE"}
        )
        self.s3_client.delete_object.assert_not_called()

    def test_process_accepts_event_size_at_limit(self) -> None:
        self.given_object(webp_bytes())

        self.processor.process(self.event(size=5_242_880))

        self.callback_client.complete.assert_called_once()

    def test_process_rejects_body_over_limit(self) -> None:
        oversized = b"x" * 5_242_881
        self.s3_client.get_object.return_value = {"Body": io.BytesIO(oversized)}

        with self.assertRaises(RejectedImageError):
            self.processor.process(self.event(size=None))

        self.callback_client.failed.assert_called_once_with(
            "dev", UPLOAD_ID, {"reason": "TOO_LARGE"}
        )

    def test_process_rejects_non_webp_image(self) -> None:
        self.given_object(webp_bytes(image_format="PNG"))

        with self.assertRaises(RejectedImageError):
            self.processor.process(self.event())

        self.callback_client.failed.assert_called_once_with(
            "dev", UPLOAD_ID, {"reason": "UNSUPPORTED_FORMAT"}
        )

    def test_process_rejects_undecodable_bytes(self) -> None:
        self.given_object(b"not an image at all")

        with self.assertRaises(RejectedImageError):
            self.processor.process(self.event())

        self.callback_client.failed.assert_called_once_with(
            "dev", UPLOAD_ID, {"reason": "UNSUPPORTED_FORMAT"}
        )

    def test_process_rejects_animated_webp(self) -> None:
        self.given_object(animated_webp_bytes())

        with self.assertRaises(RejectedImageError):
            self.processor.process(self.event())

        self.callback_client.failed.assert_called_once_with(
            "dev", UPLOAD_ID, {"reason": "ANIMATED_IMAGE"}
        )

    def test_process_rejects_image_over_pixel_limit(self) -> None:
        processor = PostImageProcessor(
            s3_client=self.s3_client,
            settings=settings().__class__(
                **{**settings().__dict__, "post_max_pixels": 100}
            ),
            callback_client=self.callback_client,
        )
        self.given_object(webp_bytes(size=(40, 30)))

        with self.assertRaises(RejectedImageError):
            processor.process(self.event())

        self.callback_client.failed.assert_called_once_with(
            "dev", UPLOAD_ID, {"reason": "TOO_MANY_PIXELS"}
        )

    def test_process_reports_missing_staging_object(self) -> None:
        from botocore.exceptions import ClientError

        self.s3_client.get_object.side_effect = ClientError(
            {"Error": {"Code": "NoSuchKey"}}, "GetObject"
        )

        with self.assertRaises(RejectedImageError):
            self.processor.process(self.event())

        self.callback_client.failed.assert_called_once_with(
            "dev", UPLOAD_ID, {"reason": "MISSING_OBJECT"}
        )

    def test_process_sends_image_size_in_complete_callback(self) -> None:
        self.given_object(webp_bytes(size=(120, 90)))

        self.processor.process(self.event())

        body = self.callback_client.complete.call_args.args[2]
        self.assertEqual(120, body["width"])
        self.assertEqual(90, body["height"])
        self.assertEqual(len(self.uploaded(ORIGINAL_KEY)["Body"]), body["byteSize"])

    def test_process_sends_null_metadata_when_image_has_no_exif(self) -> None:
        self.given_object(webp_bytes())

        self.processor.process(self.event())

        body = self.callback_client.complete.call_args.args[2]
        self.assertIsNone(body["location"])
        self.assertIsNone(body["capturedAt"])
        self.assertEqual({}, body["metaAttributes"])

    def test_process_extracts_captured_at_and_camera_attributes(self) -> None:
        self.given_object(
            webp_bytes(
                exif=exif_bytes(
                    {
                        0x9003: "2026:08:20 11:02:31",
                        0x9011: "+09:00",
                        0x010F: "Apple",
                        0x0110: "iPhone 15 Pro",
                    }
                )
            )
        )

        self.processor.process(self.event())

        body = self.callback_client.complete.call_args.args[2]
        self.assertEqual("2026-08-20T11:02:31+09:00", body["capturedAt"])
        self.assertEqual("Apple", body["metaAttributes"]["Make"])
        self.assertEqual("iPhone 15 Pro", body["metaAttributes"]["Model"])

    def test_process_keeps_captured_at_without_offset_when_absent(self) -> None:
        self.given_object(webp_bytes(exif=exif_bytes({0x9003: "2026:08:20 11:02:31"})))

        self.processor.process(self.event())

        body = self.callback_client.complete.call_args.args[2]
        self.assertEqual("2026-08-20T11:02:31", body["capturedAt"])

    def test_process_strips_exif_from_uploaded_objects(self) -> None:
        self.given_object(
            webp_bytes(exif=exif_bytes({0x010F: "Apple", 0x0110: "iPhone 15 Pro"}))
        )

        self.processor.process(self.event())

        original = Image.open(io.BytesIO(self.uploaded(ORIGINAL_KEY)["Body"]))
        self.assertEqual({}, dict(original.getexif()))
        thumbnail = Image.open(io.BytesIO(self.uploaded(THUMBNAIL_KEY)["Body"]))
        self.assertEqual({}, dict(thumbnail.getexif()))

    def test_process_truncates_oversized_metadata(self) -> None:
        processor = PostImageProcessor(
            s3_client=self.s3_client,
            settings=settings().__class__(
                **{**settings().__dict__, "post_metadata_max_bytes": 40}
            ),
            callback_client=self.callback_client,
        )
        self.given_object(
            webp_bytes(exif=exif_bytes({0x010F: "Apple", 0x0110: "iPhone 15 Pro"}))
        )

        processor.process(self.event())

        body = self.callback_client.complete.call_args.args[2]
        self.assertTrue(body["metaAttributes"]["_truncated"])


if __name__ == "__main__":
    unittest.main()
