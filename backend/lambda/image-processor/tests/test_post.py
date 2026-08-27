import io
import unittest
from dataclasses import replace
from unittest.mock import Mock

from PIL import Image
from PIL.TiffImagePlugin import IFDRational

from image_processor.callback import ProcessingUploadUrls
from image_processor.config import Settings
from image_processor.errors import RejectedImageError
from image_processor.events import S3ObjectCreated
from image_processor.post import PostImageProcessor

UPLOAD_ID = "0198d999-ff00-7000-8000-000000000001"
BUCKET = "test-bucket"
STAGING_KEY = f"chalkak/staging/dev/posts/{UPLOAD_ID}.webp"
ORIGINAL_KEY = f"chalkak/posts/dev/original/{UPLOAD_ID}.webp"
THUMBNAIL_KEY = f"chalkak/posts/dev/thumbnail/{UPLOAD_ID}.webp"
ORIGINAL_URL = "https://s3.test/original"
THUMBNAIL_URL = "https://s3.test/thumbnail"


def settings() -> Settings:
    return Settings(
        expected_bucket=BUCKET,
        root_prefix="chalkak",
        max_input_bytes=1_048_576,
        max_image_pixels=25_000_000,
        thumbnail_max_size=512,
        post_max_bytes=5_242_880,
        post_max_pixels=25_000_000,
        post_thumbnail_max_size=1080,
        post_webp_quality=85,
        post_thumbnail_webp_quality=80,
        post_metadata_max_bytes=8192,
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


EXIF_IFD_TAG = 0x8769
GPS_IFD_TAG = 0x8825

# 실제 카메라가 Exif sub-IFD에 기록하는 태그들. IFD0에 직접 심으면 규격에 없는 구조가 되어
# 프로덕션에서는 결코 재현되지 않는 픽스처가 된다.
SUB_IFD_TAGS = frozenset({
    0x829A,  # ExposureTime
    0x829D,  # FNumber
    0x8827,  # ISOSpeedRatings
    0x9003,  # DateTimeOriginal
    0x9011,  # OffsetTimeOriginal
    0xA434,  # LensModel
})


def exif_bytes(
    tags: dict[int, object],
    gps: dict[int, object] | None = None,
) -> bytes:
    exif = Image.Exif()
    sub_ifd = exif.get_ifd(EXIF_IFD_TAG)
    for tag, value in tags.items():
        if tag in SUB_IFD_TAGS:
            sub_ifd[tag] = value
            continue
        exif[tag] = value
    if gps is not None:
        exif.get_ifd(GPS_IFD_TAG).update(gps)
    return exif.tobytes()


class PostImageProcessorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.s3_client = Mock()
        self.callback_client = Mock()
        self.callback_client.issue_upload_urls.return_value = ProcessingUploadUrls(
            original_upload_url=ORIGINAL_URL,
            thumbnail_upload_url=THUMBNAIL_URL,
            content_type="image/webp",
            cache_control="public, max-age=86400",
        )
        self.upload_client = Mock()
        self.upload_client.upload.return_value = True
        self.processor = PostImageProcessor(
            s3_client=self.s3_client,
            settings=settings(),
            callback_client=self.callback_client,
            upload_client=self.upload_client,
        )

    def given_object(self, body: bytes) -> None:
        stream = io.BytesIO(body)
        self.s3_client.get_object.return_value = {
            "ContentLength": len(body),
            "Body": stream,
        }

    def event(self, size: int | None = 100, key: str = STAGING_KEY) -> S3ObjectCreated:
        return S3ObjectCreated(bucket=BUCKET, key=key, size=size)

    def uploaded(self, url: str) -> dict:
        for call in self.upload_client.upload.call_args_list:
            if call.kwargs["url"] == url:
                return call.kwargs
        raise AssertionError(f"{url} was not uploaded")

    def test_process_uploads_original_and_thumbnail_as_webp(self) -> None:
        self.given_object(webp_bytes())

        result = self.processor.process(self.event())

        self.assertEqual(ORIGINAL_KEY, result.original_key)
        self.assertEqual(THUMBNAIL_KEY, result.thumbnail_key)
        original = self.uploaded(ORIGINAL_URL)
        self.assertEqual("image/webp", original["content_type"])
        self.assertEqual("public, max-age=86400", original["cache_control"])
        self.assertEqual("WEBP", Image.open(io.BytesIO(original["body"])).format)
        thumbnail = self.uploaded(THUMBNAIL_URL)
        self.assertEqual("WEBP", Image.open(io.BytesIO(thumbnail["body"])).format)
        self.s3_client.put_object.assert_not_called()

    def test_process_restores_global_pixel_limit(self) -> None:
        previous = Image.MAX_IMAGE_PIXELS
        self.given_object(webp_bytes())

        self.processor.process(self.event())

        self.assertEqual(previous, Image.MAX_IMAGE_PIXELS)

    def test_process_restores_global_pixel_limit_after_rejection(self) -> None:
        previous = Image.MAX_IMAGE_PIXELS
        self.given_object(b"not an image at all")

        with self.assertRaises(RejectedImageError):
            self.processor.process(self.event())

        self.assertEqual(previous, Image.MAX_IMAGE_PIXELS)

    def test_process_downscales_thumbnail_within_max_size(self) -> None:
        processor = PostImageProcessor(
            s3_client=self.s3_client,
            settings=replace(settings(), post_thumbnail_max_size=32),
            callback_client=self.callback_client,
            upload_client=self.upload_client,
        )
        self.given_object(webp_bytes(size=(400, 300)))

        processor.process(self.event())

        thumbnail = Image.open(io.BytesIO(self.uploaded(THUMBNAIL_URL)["body"]))
        self.assertLessEqual(max(thumbnail.size), 32)
        original = Image.open(io.BytesIO(self.uploaded(ORIGINAL_URL)["body"]))
        self.assertEqual((400, 300), original.size)

    def test_process_uses_backend_issued_upload_urls(self) -> None:
        self.given_object(webp_bytes())

        self.processor.process(self.event())

        self.callback_client.issue_upload_urls.assert_called_once_with("dev", UPLOAD_ID)
        self.assertEqual(2, self.upload_client.upload.call_count)

    def test_process_verifies_existing_destinations_before_completing(self) -> None:
        self.upload_client.upload.return_value = False
        source = webp_bytes()

        def get_object(**kwargs):
            if kwargs["Key"] == STAGING_KEY:
                body = source
            else:
                call_index = 0 if "/original/" in kwargs["Key"] else 1
                body = self.upload_client.upload.call_args_list[call_index].kwargs[
                    "body"
                ]
            return {
                "ContentLength": len(body),
                "Body": io.BytesIO(body),
            }

        self.s3_client.get_object.side_effect = get_object

        self.processor.process(self.event())

        self.assertEqual(3, self.s3_client.get_object.call_count)
        self.callback_client.complete.assert_called_once()
        self.s3_client.delete_object.assert_called_once_with(
            Bucket=BUCKET, Key=STAGING_KEY
        )

    def test_process_does_not_complete_when_existing_destination_differs(self) -> None:
        self.upload_client.upload.return_value = False
        source = webp_bytes()
        self.s3_client.get_object.side_effect = [
            {
                "ContentLength": len(source),
                "Body": io.BytesIO(source),
            },
            {
                "ContentLength": len(b"different-image"),
                "Body": io.BytesIO(b"different-image"),
            },
        ]

        with self.assertRaisesRegex(RuntimeError, "does not match"):
            self.processor.process(self.event())

        self.callback_client.complete.assert_not_called()
        self.s3_client.delete_object.assert_not_called()

    def test_process_propagates_other_upload_errors(self) -> None:
        self.upload_client.upload.side_effect = TimeoutError("upload timeout")
        self.given_object(webp_bytes())

        with self.assertRaises(TimeoutError):
            self.processor.process(self.event())

        self.callback_client.complete.assert_not_called()

    def test_process_keeps_staging_when_upload_url_issuance_fails(self) -> None:
        self.callback_client.issue_upload_urls.side_effect = TimeoutError(
            "backend timeout"
        )
        self.given_object(webp_bytes())

        with self.assertRaises(TimeoutError):
            self.processor.process(self.event())

        self.upload_client.upload.assert_not_called()
        self.callback_client.complete.assert_not_called()
        self.s3_client.delete_object.assert_not_called()

    def test_abandon_closes_upload_and_removes_staging(self) -> None:
        self.processor.abandon(self.event())

        self.callback_client.failed.assert_called_once_with(
            "dev", UPLOAD_ID, {"reason": "PROCESSING_ERROR"}
        )
        self.s3_client.delete_object.assert_called_once_with(
            Bucket=BUCKET, Key=STAGING_KEY
        )

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

        thumbnail = Image.open(io.BytesIO(self.uploaded(THUMBNAIL_URL)["body"]))
        self.assertEqual((1080, 540), thumbnail.size)

    def test_process_keeps_original_resolution(self) -> None:
        self.given_object(webp_bytes(size=(2160, 1080)))

        self.processor.process(self.event())

        original = Image.open(io.BytesIO(self.uploaded(ORIGINAL_URL)["body"]))
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
        self.s3_client.delete_object.assert_called_once_with(
            Bucket=BUCKET, Key=STAGING_KEY
        )

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
            "dev", UPLOAD_ID, {"reason": "CORRUPTED_IMAGE"}
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
            settings=replace(settings(), post_max_pixels=100),
            callback_client=self.callback_client,
            upload_client=self.upload_client,
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

    def test_process_deletes_staging_object_when_image_is_rejected(self) -> None:
        self.given_object(b"not an image at all")

        with self.assertRaises(RejectedImageError):
            self.processor.process(self.event())

        self.s3_client.delete_object.assert_called_once_with(
            Bucket=BUCKET, Key=STAGING_KEY
        )

    def test_process_keeps_staging_object_when_delete_fails(self) -> None:
        from botocore.exceptions import ClientError

        self.s3_client.delete_object.side_effect = ClientError(
            {"Error": {"Code": "AccessDenied"}}, "DeleteObject"
        )
        self.given_object(b"not an image at all")

        with self.assertRaises(RejectedImageError):
            self.processor.process(self.event())

        self.callback_client.failed.assert_called_once_with(
            "dev", UPLOAD_ID, {"reason": "CORRUPTED_IMAGE"}
        )

    def test_process_closes_upload_when_complete_callback_is_refused(self) -> None:
        from image_processor.errors import PermanentCallbackError

        self.callback_client.complete.side_effect = PermanentCallbackError("400")
        self.given_object(webp_bytes())

        with self.assertRaises(PermanentCallbackError):
            self.processor.process(self.event())

        self.callback_client.failed.assert_called_once_with(
            "dev", UPLOAD_ID, {"reason": "PROCESSING_ERROR"}
        )
        self.s3_client.delete_object.assert_not_called()

    def test_process_sends_image_size_in_complete_callback(self) -> None:
        self.given_object(webp_bytes(size=(120, 90)))

        self.processor.process(self.event())

        body = self.callback_client.complete.call_args.args[2]
        self.assertEqual(120, body["width"])
        self.assertEqual(90, body["height"])
        self.assertEqual(len(self.uploaded(ORIGINAL_URL)["body"]), body["byteSize"])

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

    def test_process_extracts_camera_settings_from_exif_sub_ifd(self) -> None:
        self.given_object(
            webp_bytes(
                exif=exif_bytes(
                    {
                        0x010F: "Apple",
                        0x829A: IFDRational(1, 200),
                        0x8827: 400,
                        0xA434: "iPhone 15 Pro back camera",
                    }
                )
            )
        )

        self.processor.process(self.event())

        attributes = self.callback_client.complete.call_args.args[2]["metaAttributes"]
        self.assertEqual("Apple", attributes["Make"])
        self.assertEqual("0.005", attributes["ExposureTime"])
        self.assertEqual(400, attributes["ISOSpeedRatings"])
        self.assertEqual("iPhone 15 Pro back camera", attributes["LensModel"])

    def test_process_excludes_ifd_pointers_and_orientation_from_meta_attributes(
        self,
    ) -> None:
        self.given_object(
            webp_bytes(
                exif=exif_bytes(
                    {0x010F: "Apple", 0x0112: 6, 0x9003: "2026:08:20 11:02:31"},
                    gps={1: "N", 2: (37.0, 33.0, 59.4), 3: "E", 4: (126.0, 58.0, 40.8)},
                )
            )
        )

        self.processor.process(self.event())

        attributes = self.callback_client.complete.call_args.args[2]["metaAttributes"]
        self.assertEqual("Apple", attributes["Make"])
        self.assertNotIn("ExifOffset", attributes)
        self.assertNotIn("GPSInfo", attributes)
        self.assertNotIn("Orientation", attributes)
        self.assertNotIn("DateTimeOriginal", attributes)

    def test_process_keeps_captured_at_without_offset_when_absent(self) -> None:
        self.given_object(webp_bytes(exif=exif_bytes({0x9003: "2026:08:20 11:02:31"})))

        self.processor.process(self.event())

        body = self.callback_client.complete.call_args.args[2]
        self.assertEqual("2026-08-20T11:02:31", body["capturedAt"])

    def test_process_extracts_location_from_gps_ifd(self) -> None:
        self.given_object(
            webp_bytes(
                exif=exif_bytes(
                    {},
                    gps={1: "N", 2: (37.0, 33.0, 59.4), 3: "E", 4: (126.0, 58.0, 40.8)},
                )
            )
        )

        self.processor.process(self.event())

        location = self.callback_client.complete.call_args.args[2]["location"]
        self.assertAlmostEqual(37.5665, location["latitude"], places=4)
        self.assertAlmostEqual(126.978, location["longitude"], places=4)

    def test_process_negates_southern_and_western_coordinates(self) -> None:
        self.given_object(
            webp_bytes(
                exif=exif_bytes(
                    {},
                    gps={1: "S", 2: (37.0, 33.0, 59.4), 3: "W", 4: (126.0, 58.0, 40.8)},
                )
            )
        )

        self.processor.process(self.event())

        location = self.callback_client.complete.call_args.args[2]["location"]
        self.assertAlmostEqual(-37.5665, location["latitude"], places=4)
        self.assertAlmostEqual(-126.978, location["longitude"], places=4)

    def test_process_ignores_gps_tag_that_is_not_three_rationals(self) -> None:
        self.given_object(
            webp_bytes(
                exif=exif_bytes(
                    {},
                    gps={1: "N", 2: (37.0, 33.0), 3: "E", 4: (126.0, 58.0, 40.8)},
                )
            )
        )

        self.processor.process(self.event())

        self.assertIsNone(self.callback_client.complete.call_args.args[2]["location"])

    def test_process_ignores_out_of_range_coordinates(self) -> None:
        self.given_object(
            webp_bytes(
                exif=exif_bytes(
                    {},
                    gps={1: "N", 2: (91.0, 0.0, 0.0), 3: "E", 4: (126.0, 58.0, 40.8)},
                )
            )
        )

        self.processor.process(self.event())

        self.assertIsNone(self.callback_client.complete.call_args.args[2]["location"])

    def test_process_ignores_oversized_captured_at(self) -> None:
        self.given_object(
            webp_bytes(exif=exif_bytes({0x9003: "A " + "B" * 5_000}))
        )

        self.processor.process(self.event())

        self.assertIsNone(self.callback_client.complete.call_args.args[2]["capturedAt"])

    def test_process_ignores_malformed_captured_at(self) -> None:
        for captured in (
            "2026:13:40 11:02:31",
            "not a timestamp at all",
            "2026:08:20 25:02:31",
        ):
            with self.subTest(captured=captured):
                self.callback_client.reset_mock()
                self.given_object(webp_bytes(exif=exif_bytes({0x9003: captured})))

                self.processor.process(self.event())

                body = self.callback_client.complete.call_args.args[2]
                self.assertIsNone(body["capturedAt"])

    def test_process_ignores_malformed_captured_at_offset(self) -> None:
        self.given_object(
            webp_bytes(
                exif=exif_bytes({0x9003: "2026:08:20 11:02:31", 0x9011: "nine hours"})
            )
        )

        self.processor.process(self.event())

        self.assertIsNone(self.callback_client.complete.call_args.args[2]["capturedAt"])

    def test_process_strips_exif_from_uploaded_objects(self) -> None:
        self.given_object(
            webp_bytes(exif=exif_bytes({0x010F: "Apple", 0x0110: "iPhone 15 Pro"}))
        )

        self.processor.process(self.event())

        original = Image.open(io.BytesIO(self.uploaded(ORIGINAL_URL)["body"]))
        self.assertEqual({}, dict(original.getexif()))
        thumbnail = Image.open(io.BytesIO(self.uploaded(THUMBNAIL_URL)["body"]))
        self.assertEqual({}, dict(thumbnail.getexif()))

    def test_process_truncates_oversized_metadata(self) -> None:
        processor = PostImageProcessor(
            s3_client=self.s3_client,
            settings=replace(settings(), post_metadata_max_bytes=40),
            callback_client=self.callback_client,
            upload_client=self.upload_client,
        )
        self.given_object(
            webp_bytes(exif=exif_bytes({0x010F: "Apple", 0x0110: "iPhone 15 Pro"}))
        )

        processor.process(self.event())

        body = self.callback_client.complete.call_args.args[2]
        self.assertTrue(body["metaAttributes"]["_truncated"])


if __name__ == "__main__":
    unittest.main()
