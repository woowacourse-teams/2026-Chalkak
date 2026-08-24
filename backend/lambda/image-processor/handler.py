import json
import logging
from typing import Any

import boto3

from image_processor.config import Settings
from image_processor.errors import RejectedEventError, RejectedImageError
from image_processor.events import parse_s3_records
from image_processor.router import ImageProcessorRouter
from image_processor.signature import SignatureImageProcessor

LOGGER = logging.getLogger()
LOGGER.setLevel(logging.INFO)

_processor: ImageProcessorRouter | None = None


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, int]:
    processed_count = 0
    rejected_count = 0

    for sqs_record in event.get("Records", []):
        message_id = sqs_record.get("messageId", "unknown")
        try:
            s3_records = parse_s3_records(sqs_record.get("body", ""))
        except RejectedEventError as exception:
            rejected_count += 1
            _log_rejection(message_id, str(exception))
            continue

        for s3_record in s3_records:
            try:
                _get_processor().process(s3_record)
                processed_count += 1
            except RejectedImageError as exception:
                rejected_count += 1
                _log_rejection(
                    message_id,
                    str(exception),
                    bucket=s3_record.bucket,
                    key=s3_record.key,
                )
            except Exception:
                LOGGER.exception(
                    json.dumps(
                        {
                            "event": "image_processing_failed",
                            "messageId": message_id,
                            "bucket": s3_record.bucket,
                            "key": s3_record.key,
                        }
                    )
                )
                raise

    return {
        "processedCount": processed_count,
        "rejectedCount": rejected_count,
    }


def _log_rejection(
    message_id: str,
    reason: str,
    bucket: str | None = None,
    key: str | None = None,
) -> None:
    LOGGER.warning(
        json.dumps(
            {
                "event": "image_processing_rejected",
                "messageId": message_id,
                "bucket": bucket,
                "key": key,
                "reason": reason,
            },
            ensure_ascii=False,
        )
    )


def _get_processor() -> ImageProcessorRouter:
    global _processor
    if _processor is None:
        settings = Settings.from_environment()
        signature_processor = SignatureImageProcessor(
            s3_client=boto3.client("s3"),
            settings=settings,
        )
        _processor = ImageProcessorRouter(
            signature_processor=signature_processor,
            root_prefix=settings.root_prefix,
        )
    return _processor
