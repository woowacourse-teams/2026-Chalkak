import json
import logging
import os
from typing import Any

import boto3

from image_processor.callback import ProcessingCallbackClient
from image_processor.config import Settings
from image_processor.errors import (
    PermanentCallbackError,
    RejectedEventError,
    RejectedImageError,
)
from image_processor.events import parse_s3_records
from image_processor.post import PostImageProcessor
from image_processor.router import ImageProcessorRouter
from image_processor.signature import SignatureImageProcessor

LOGGER = logging.getLogger()
LOGGER.setLevel(logging.INFO)

_processor: ImageProcessorRouter | None = None


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    # 설정 오류는 메시지 단위 실패가 아니라 인보케이션 실패로 드러나야 한다. 레코드 루프 안에서
    # 만들면 필수 환경변수 누락이 모든 메시지의 일반 예외로 보이고 원인이 로그에 남지 않는다.
    processor = _get_processor()
    partial_batch_response = _partial_batch_response_enabled()
    processed_count = 0
    rejected_count = 0
    failed_message_ids: list[str] = []

    for sqs_record in event.get("Records", []):
        message_id = sqs_record.get("messageId", "unknown")
        try:
            processed, rejected = _process_message(processor, sqs_record, message_id)
        except Exception:
            if not partial_batch_response:
                raise
            failed_message_ids.append(message_id)
            continue
        processed_count += processed
        rejected_count += rejected

    if partial_batch_response:
        return {
            "batchItemFailures": [
                {"itemIdentifier": message_id} for message_id in failed_message_ids
            ]
        }

    return {
        "processedCount": processed_count,
        "rejectedCount": rejected_count,
    }


def _process_message(
    processor: ImageProcessorRouter,
    sqs_record: dict[str, Any],
    message_id: str,
) -> tuple[int, int]:
    processed_count = 0
    rejected_count = 0
    try:
        s3_records = parse_s3_records(sqs_record.get("body", ""))
    except RejectedEventError as exception:
        _log_rejection(message_id, str(exception))
        return 0, 1

    for s3_record in s3_records:
        try:
            processor.process(s3_record)
            processed_count += 1
        except RejectedImageError as exception:
            rejected_count += 1
            _log_rejection(
                message_id,
                str(exception),
                bucket=s3_record.bucket,
                key=s3_record.key,
            )
        except PermanentCallbackError as exception:
            rejected_count += 1
            LOGGER.error(
                json.dumps(
                    {
                        "event": "callback_permanently_refused",
                        "messageId": message_id,
                        "bucket": s3_record.bucket,
                        "key": s3_record.key,
                        "reason": str(exception),
                    }
                )
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

    return processed_count, rejected_count


def _partial_batch_response_enabled() -> bool:
    """
    부분 배치 응답은 이벤트 소스 매핑에 ReportBatchItemFailures가 켜져 있을 때만 유효하다. 꺼진 채로
    실패를 반환하면 Lambda가 응답을 무시해 실패한 메시지가 조용히 삭제되므로, 인프라 설정과 짝을
    맞춰 켜도록 기본값을 끔으로 둔다.
    """
    return os.environ.get("SQS_PARTIAL_BATCH_RESPONSE", "false").strip().lower() == "true"


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
        callback_client = ProcessingCallbackClient(
            kind="signature-processing",
            base_urls={
                "dev": settings.dev_callback_base_url,
                "prod": settings.prod_callback_base_url,
            },
            secret=settings.callback_secret,
            timeout_seconds=settings.callback_timeout_seconds,
        )
        post_callback_client = ProcessingCallbackClient(
            kind="post-image-processing",
            base_urls={
                "dev": settings.dev_post_callback_base_url,
                "prod": settings.prod_post_callback_base_url,
            },
            secret=settings.callback_secret,
            timeout_seconds=settings.callback_timeout_seconds,
        )
        s3_client = boto3.client("s3")
        signature_processor = SignatureImageProcessor(
            s3_client=s3_client,
            settings=settings,
            callback_client=callback_client,
        )
        post_processor = PostImageProcessor(
            s3_client=s3_client,
            settings=settings,
            callback_client=post_callback_client,
        )
        _processor = ImageProcessorRouter(
            signature_processor=signature_processor,
            post_processor=post_processor,
            root_prefix=settings.root_prefix,
        )
    return _processor
