from image_processor.errors import RejectedImageError
from image_processor.events import S3ObjectCreated
from image_processor.post import PostImageProcessor
from image_processor.signature import SignatureImageProcessor


class ImageProcessorRouter:
    def __init__(
        self,
        signature_processor: SignatureImageProcessor,
        post_processor: PostImageProcessor,
        root_prefix: str,
    ):
        staging_prefix = f"{root_prefix}/staging"
        self._signature_prefixes = tuple(
            f"{staging_prefix}/{environment}/signatures/"
            for environment in ("dev", "prod")
        )
        self._post_prefixes = tuple(
            f"{staging_prefix}/{environment}/posts/"
            for environment in ("dev", "prod")
        )
        self._signature_processor = signature_processor
        self._post_processor = post_processor

    def process(self, event: S3ObjectCreated) -> object:
        return self._route(event).process(event)

    def abandon(self, event: S3ObjectCreated) -> None:
        self._route(event).abandon(event)

    def _route(self, event: S3ObjectCreated):
        if event.key.startswith(self._signature_prefixes):
            return self._signature_processor
        if event.key.startswith(self._post_prefixes):
            return self._post_processor
        raise RejectedImageError("unsupported staging path")
