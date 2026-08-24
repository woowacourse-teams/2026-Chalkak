import json
import unittest
from unittest.mock import Mock, patch

import handler
from image_processor.errors import RejectedImageError


def sqs_event(body: dict, message_id: str = "message-1") -> dict:
    return {
        "Records": [
            {
                "messageId": message_id,
                "body": json.dumps(body),
            }
        ]
    }


def s3_body() -> dict:
    return {
        "Records": [
            {
                "eventSource": "aws:s3",
                "eventName": "ObjectCreated:Put",
                "s3": {
                    "bucket": {"name": "test-bucket"},
                    "object": {
                        "key": (
                            "chalkak/staging/dev/signatures/"
                            "0198d999-ff00-7000-8000-000000000001.png"
                        ),
                        "size": 100,
                    },
                },
            }
        ]
    }


class LambdaHandlerTest(unittest.TestCase):
    def tearDown(self) -> None:
        handler._processor = None

    def test_handler_processes_s3_message_inside_sqs(self) -> None:
        processor = Mock()
        handler._processor = processor

        result = handler.lambda_handler(sqs_event(s3_body()), None)

        self.assertEqual({"processedCount": 1, "rejectedCount": 0}, result)
        processor.process.assert_called_once()

    def test_handler_acknowledges_rejected_image_without_retry(self) -> None:
        processor = Mock()
        processor.process.side_effect = RejectedImageError("invalid image")
        handler._processor = processor

        with patch.object(handler.LOGGER, "warning") as warning:
            result = handler.lambda_handler(sqs_event(s3_body()), None)

        self.assertEqual({"processedCount": 0, "rejectedCount": 1}, result)
        warning.assert_called_once()

    def test_handler_continues_after_one_s3_record_is_rejected(self) -> None:
        processor = Mock()
        processor.process.side_effect = [RejectedImageError("invalid image"), None]
        handler._processor = processor
        body = s3_body()
        body["Records"].append(body["Records"][0].copy())

        with patch.object(handler.LOGGER, "warning"):
            result = handler.lambda_handler(sqs_event(body), None)

        self.assertEqual({"processedCount": 1, "rejectedCount": 1}, result)
        self.assertEqual(2, processor.process.call_count)

    def test_handler_acknowledges_malformed_queue_message(self) -> None:
        handler._processor = Mock()
        event = {"Records": [{"messageId": "message-1", "body": "not-json"}]}

        with patch.object(handler.LOGGER, "warning"):
            result = handler.lambda_handler(event, None)

        self.assertEqual({"processedCount": 0, "rejectedCount": 1}, result)
        handler._processor.process.assert_not_called()

    def test_handler_raises_transient_failure_for_sqs_retry(self) -> None:
        processor = Mock()
        processor.process.side_effect = TimeoutError("S3 timeout")
        handler._processor = processor

        with patch.object(handler.LOGGER, "exception"):
            with self.assertRaises(TimeoutError):
                handler.lambda_handler(sqs_event(s3_body()), None)


if __name__ == "__main__":
    unittest.main()
