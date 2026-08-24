from image_processor.errors import RejectedImageError
from image_processor.events import S3ObjectCreated
from image_processor.signature import SignatureImageProcessor


class ImageProcessorRouter:
    def __init__(
        self,
        signature_processor: SignatureImageProcessor,
        root_prefix: str,
    ):
        staging_prefix = f"{root_prefix}/staging"
        self._signature_prefix = f"{staging_prefix}/signatures/"
        self._post_prefix = f"{staging_prefix}/posts/"
        self._signature_processor = signature_processor

    def process(self, event: S3ObjectCreated) -> object:
        if event.key.startswith(self._signature_prefix):
            return self._signature_processor.process(event)
        if event.key.startswith(self._post_prefix):
            raise RejectedImageError("post image processing is not implemented")
        raise RejectedImageError("unsupported staging path")
