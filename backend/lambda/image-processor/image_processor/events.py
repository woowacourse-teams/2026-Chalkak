import json
from dataclasses import dataclass
from typing import Any
from urllib.parse import unquote_plus

from image_processor.errors import RejectedEventError


@dataclass(frozen=True)
class S3ObjectCreated:
    bucket: str
    key: str
    size: int | None


def parse_s3_records(body: str) -> list[S3ObjectCreated]:
    payload = _parse_json(body)
    payload = _unwrap_sns_notification(payload)

    if payload.get("Event") == "s3:TestEvent":
        return []

    records = payload.get("Records")
    if not isinstance(records, list) or not records:
        raise RejectedEventError("S3 records are missing")

    return [_parse_s3_record(record) for record in records]


def _parse_json(value: str) -> dict[str, Any]:
    try:
        payload = json.loads(value)
    except (json.JSONDecodeError, TypeError) as exception:
        raise RejectedEventError("message body is not valid JSON") from exception

    if not isinstance(payload, dict):
        raise RejectedEventError("message body must be a JSON object")
    return payload


def _unwrap_sns_notification(payload: dict[str, Any]) -> dict[str, Any]:
    if payload.get("Type") != "Notification":
        return payload

    message = payload.get("Message")
    if not isinstance(message, str):
        raise RejectedEventError("SNS notification message is missing")
    return _parse_json(message)


def _parse_s3_record(record: Any) -> S3ObjectCreated:
    try:
        if record["eventSource"] != "aws:s3":
            raise RejectedEventError("record is not an S3 event")
        if not record["eventName"].startswith("ObjectCreated:"):
            raise RejectedEventError("record is not an object-created event")

        bucket = record["s3"]["bucket"]["name"]
        object_data = record["s3"]["object"]
        key = unquote_plus(object_data["key"])
        size = object_data.get("size")
    except (KeyError, TypeError, AttributeError) as exception:
        raise RejectedEventError("S3 record is malformed") from exception

    if not isinstance(bucket, str) or not bucket:
        raise RejectedEventError("S3 bucket name is missing")
    if not isinstance(key, str) or not key:
        raise RejectedEventError("S3 object key is missing")
    if size is not None and (not isinstance(size, int) or size < 0):
        raise RejectedEventError("S3 object size is invalid")

    return S3ObjectCreated(bucket=bucket, key=key, size=size)
