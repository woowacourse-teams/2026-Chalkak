"""Jev 전용 큐 작업. 이미지 처리 실패 콜백으로 검수 오류를 보내지 않는다."""

import base64
import http.client
import io
import json
import logging
import signal
import socket
import time
import uuid
import warnings
from contextlib import contextmanager
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from typing import Any

from PIL import Image, ImageOps, UnidentifiedImageError

from image_processor.config import Settings

LOGGER = logging.getLogger(__name__)
MAX_FINAL_IMAGE_BYTES = 32 * 1024 * 1024


class JevImageError(ValueError):
    pass


def create_jev_png(source: bytes, max_pixels: int = 25_000_000) -> bytes:
    """짧은 변 300, 비율 유지. 공급자 상한을 넘으면 자르거나 여백을 만들지 않는다."""
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(io.BytesIO(source)) as image:
                if image.width * image.height > max_pixels or getattr(image, "n_frames", 1) != 1:
                    raise JevImageError("unsupported image dimensions or animation")
                image.load()
                normalized = ImageOps.exif_transpose(image)
                width, height = normalized.size
                scale = 300 / min(width, height)
                size = (round(width * scale), round(height * scale))
                if min(size) != 300 or max(size) > 1568:
                    raise JevImageError("aspect ratio exceeds provider limits")
                mode = "RGBA" if "A" in normalized.getbands() or "transparency" in normalized.info else "RGB"
                resized = normalized.convert(mode).resize(size, Image.Resampling.LANCZOS)
                # 새 이미지에 픽셀만 옮겨 EXIF/ICC/텍스트 등 입력 메타데이터를 전부 제거한다.
                clean = Image.new(mode, size)
                clean.paste(resized)
                output = io.BytesIO()
                clean.save(output, format="PNG")
                encoded = output.getvalue()
                if len(encoded) > 16 * 1024 * 1024:
                    raise JevImageError("PNG exceeds provider byte limit")
                return encoded
    except (OSError, ValueError, UnidentifiedImageError, Image.DecompressionBombWarning,
            Image.DecompressionBombError) as error:
        raise JevImageError("image preprocessing failed") from error


@contextmanager
def _hard_timeout(seconds: float):
    """Lambda Linux의 순차 handler에서 socket/전체 HTTP를 포함해 물리 호출을 제한한다."""
    previous_handler = signal.getsignal(signal.SIGALRM)
    previous_timer = signal.getitimer(signal.ITIMER_REAL)
    start = time.monotonic()

    def expired(_signal, _frame):
        raise TimeoutError("Jev HTTP deadline exceeded")

    signal.signal(signal.SIGALRM, expired)
    signal.setitimer(signal.ITIMER_REAL, max(0.001, seconds))
    try:
        yield
    finally:
        signal.setitimer(signal.ITIMER_REAL, 0)
        signal.signal(signal.SIGALRM, previous_handler)
        if previous_timer[0] > 0:
            signal.setitimer(
                signal.ITIMER_REAL,
                max(0.001, previous_timer[0] - (time.monotonic() - start)),
                previous_timer[1],
            )


class JevHttpClient:
    def __init__(self, api_key: str, connection_factory=http.client.HTTPSConnection):
        self._api_key = api_key
        self._connection_factory = connection_factory

    def execute(self, body: dict[str, Any], budget_seconds: float) -> dict[str, Any]:
        result = dict(rawResponse=None, errorCode=None, httpStatus=None,
                      latencyMs=None, retryAfterSeconds=None)
        if not self._api_key:
            return {**result, "errorCode": "CONFIGURATION_ERROR"}
        connection = None
        start = time.monotonic()
        connected = False
        try:
            with _hard_timeout(min(30.0, budget_seconds)):
                connection = self._connection_factory("api.typesafe.ai", timeout=3.0)
                connection.connect()
                connected = True
                connection.sock.settimeout(min(30.0, budget_seconds))
                headers = {"Authorization": "Bearer " + self._api_key,
                           "Content-Type": "application/json"}
                if "assets" in body:
                    headers["TypeSafe-Danger-Zone-Images"] = "true"
                connection.request("POST", "/v1/systemone",
                                   body=json.dumps(body).encode("utf-8"), headers=headers)
                response = connection.getresponse()
                result["httpStatus"] = response.status
                raw = response.read(65_537)
                if len(raw) > 65_536:
                    result["errorCode"] = "RESPONSE_TOO_LARGE"
                else:
                    result["rawResponse"] = raw.decode("utf-8")
                    if response.status != 200:
                        result["errorCode"] = "HTTP_ERROR"
                result["retryAfterSeconds"] = _retry_after(response.getheader("Retry-After"))
        except (TimeoutError, socket.timeout):
            result["errorCode"] = "TIMEOUT"
        except (OSError, http.client.HTTPException):
            result["errorCode"] = "NETWORK_ERROR" if connected else "CONNECT_ERROR"
        except UnicodeError:
            result["errorCode"] = "INVALID_RESPONSE"
        except ValueError:
            result["errorCode"] = "CONFIGURATION_ERROR"
        finally:
            result["latencyMs"] = round((time.monotonic() - start) * 1000)
            if connection is not None:
                connection.close()
        return result


def _retry_after(value: str | None) -> int | None:
    if value is None:
        return None
    try:
        seconds = int(value)
    except ValueError:
        try:
            seconds = int((parsedate_to_datetime(value) - datetime.now(timezone.utc)).total_seconds())
        except (TypeError, ValueError, OverflowError):
            return None
    return min(86400, max(0, seconds))


class JevJobProcessor:
    def __init__(self, settings: Settings, s3_client: Any, sqs_client: Any,
                 callback_client: Any, http_client: Any):
        self._settings = settings
        self._s3 = s3_client
        self._sqs = sqs_client
        self._callbacks = callback_client
        self._http = http_client

    def process(self, message: dict[str, Any]) -> None:
        environment = message["environment"]
        job_id = str(uuid.UUID(message["jobId"]))
        if environment not in {"dev", "prod"}:
            raise ValueError("unsupported Jev environment")
        if message["type"] == "JEV_RESULT":
            self._callbacks.complete_jev_call(environment, job_id, message["result"])
            return
        # 각 물리 호출은 서버 소유권을 새로 얻는다. WAIT/STOP이면 자는 대신 Lambda를 종료한다.
        for _ in range(6):
            claim = self._callbacks.claim_jev_job(environment, job_id)
            if claim["action"] in {"WAIT", "STOP"}:
                return
            if claim["action"] not in {"CLASSIFY", "INSPECT"} or claim.get("model") != "jev-1.14.0":
                raise ValueError("invalid Jev job claim")
            result = self._execute(claim, environment)
            result.update(callId=claim["callId"], stage="CLASSIFICATION" if claim["action"] == "CLASSIFY" else "PHOTO")
            if len(json.dumps(result).encode("ascii")) > 60_000:
                # 내부 콜백의 64KiB 상한도 지킨다. 잘린 JSON을 정상 응답으로 보내지 않는다.
                result.update(rawResponse=None, errorCode="RESPONSE_TOO_LARGE")
            # 먼저 같은 큐에 결과를 보존한다. 콜백 응답이 유실돼도 Jev를 다시 부르지 않고 결과만 전달한다.
            queued = False
            if self._settings.jev_work_queue_url:
                try:
                    self._sqs.send_message(
                        QueueUrl=self._settings.jev_work_queue_url,
                        MessageBody=json.dumps(dict(type="JEV_RESULT", environment=environment,
                                                    jobId=job_id, result=result)),
                    )
                    queued = True
                except Exception:
                    LOGGER.warning("Jev result enqueue failed jobId=%s callId=%s", job_id, claim["callId"])
            try:
                self._callbacks.complete_jev_call(environment, job_id, result)
            except Exception:
                if queued:
                    return
                raise RuntimeError("Jev result delivery failed") from None

    def _execute(self, claim: dict[str, Any], environment: str) -> dict[str, Any]:
        body = dict(model=claim["model"], state={"topic": claim["topic"]},
                    questions=claim["questions"])
        if not self._settings.jev_work_queue_url:
            return self._failure("CONFIGURATION_ERROR")
        deadline = datetime.fromisoformat(claim["deadline"].replace("Z", "+00:00"))
        remaining = (deadline - datetime.now(timezone.utc)).total_seconds()
        if remaining <= 0:
            return self._failure("TIMEOUT")
        if claim["action"] == "INSPECT":
            try:
                with _hard_timeout(min(8.0, remaining)):
                    png = self._load_png(claim, environment)
            except JevImageError:
                return self._failure("IMAGE_PREPROCESSING_ERROR")
            except Exception:
                return self._failure("IMAGE_READ_ERROR")
            reference = str(uuid.uuid4())
            body["state"].update(photo=reference, category=claim.get("category"))
            body["assets"] = {reference: dict(type="image", format="png",
                                             data_base64=base64.b64encode(png).decode("ascii"))}
        remaining = (deadline - datetime.now(timezone.utc)).total_seconds()
        if remaining <= 0:
            return self._failure("TIMEOUT")
        return self._http.execute(body, min(30.0, remaining))

    def _load_png(self, claim: dict[str, Any], environment: str) -> bytes:
        key = claim["storageKey"]
        expected_prefix = f"{self._settings.root_prefix}/posts/{environment}/original/"
        if not key.startswith(expected_prefix) or not key.endswith(".webp"):
            raise ValueError("invalid final image key")
        source = self._s3.get_object(Bucket=self._settings.expected_bucket, Key=key)
        stream = source["Body"]
        try:
            # 최종 WebP는 재인코딩으로 staging 입력(5MiB)보다 커질 수 있다.
            if source.get("ContentLength", 0) > MAX_FINAL_IMAGE_BYTES:
                raise ValueError("final image exceeds read limit")
            raw = stream.read(MAX_FINAL_IMAGE_BYTES + 1)
        finally:
            stream.close()
        if len(raw) > MAX_FINAL_IMAGE_BYTES:
            raise ValueError("final image exceeds read limit")
        return create_jev_png(raw, self._settings.post_max_pixels)

    @staticmethod
    def _failure(code: str) -> dict[str, Any]:
        return dict(rawResponse=None, errorCode=code, httpStatus=None,
                    latencyMs=None, retryAfterSeconds=None)
