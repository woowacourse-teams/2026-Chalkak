import json
import unittest

from image_processor.errors import RejectedEventError
from image_processor.events import parse_s3_records


class S3EventParserTest(unittest.TestCase):
    def test_parse_decodes_s3_object_key(self) -> None:
        body = json.dumps(
            {
                "Records": [
                    {
                        "eventSource": "aws:s3",
                        "eventName": "ObjectCreated:Put",
                        "s3": {
                            "bucket": {"name": "test-bucket"},
                            "object": {
                                "key": "chalkak/staging/dev/signatures/test%2Bimage.png",
                                "size": 100,
                            },
                        },
                    }
                ]
            }
        )

        records = parse_s3_records(body)

        self.assertEqual(
            "chalkak/staging/dev/signatures/test+image.png",
            records[0].key,
        )

    def test_parse_ignores_s3_test_event(self) -> None:
        records = parse_s3_records(json.dumps({"Event": "s3:TestEvent"}))

        self.assertEqual([], records)

    def test_parse_rejects_malformed_message(self) -> None:
        with self.assertRaises(RejectedEventError):
            parse_s3_records("not-json")


if __name__ == "__main__":
    unittest.main()
