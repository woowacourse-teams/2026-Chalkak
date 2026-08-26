import unittest
from unittest.mock import Mock

from image_processor.errors import RejectedImageError
from image_processor.events import S3ObjectCreated
from image_processor.router import ImageProcessorRouter

UPLOAD_ID = "0198d999-ff00-7000-8000-000000000001"


class ImageProcessorRouterTest(unittest.TestCase):
    def setUp(self) -> None:
        self.signature_processor = Mock()
        self.post_processor = Mock()
        self.router = ImageProcessorRouter(
            signature_processor=self.signature_processor,
            post_processor=self.post_processor,
            root_prefix="chalkak",
        )

    def test_process_routes_signature_directory_to_signature_processor(self) -> None:
        event = created_event(f"chalkak/staging/dev/signatures/{UPLOAD_ID}.png")

        self.router.process(event)

        self.signature_processor.process.assert_called_once_with(event)

    def test_process_routes_post_directory_to_post_processor(self) -> None:
        event = created_event(f"chalkak/staging/prod/posts/{UPLOAD_ID}.webp")

        self.router.process(event)

        self.post_processor.process.assert_called_once_with(event)
        self.signature_processor.process.assert_not_called()

    def test_process_rejects_unknown_staging_directory(self) -> None:
        event = created_event(f"chalkak/staging/dev/unknown/{UPLOAD_ID}.png")

        with self.assertRaisesRegex(RejectedImageError, "unsupported staging path"):
            self.router.process(event)

        self.signature_processor.process.assert_not_called()
        self.post_processor.process.assert_not_called()

    def test_process_rejects_unsupported_environment(self) -> None:
        event = created_event(f"chalkak/staging/test/signatures/{UPLOAD_ID}.png")

        with self.assertRaisesRegex(RejectedImageError, "unsupported staging path"):
            self.router.process(event)

        self.signature_processor.process.assert_not_called()


def created_event(key: str) -> S3ObjectCreated:
    return S3ObjectCreated(bucket="test-bucket", key=key, size=100)


if __name__ == "__main__":
    unittest.main()
