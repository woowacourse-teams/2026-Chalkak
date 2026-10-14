"""동일한 네이티브 스레드의 다중 턴·실제 압축 검사. 표준 입출력만 사용한다."""
import json
import os
from pathlib import Path
import selectors
import signal
import subprocess
import time


class Protocol:
    def __init__(self, process, output, deadline):
        self.process, self.output, self.deadline = process, output, deadline
        self.selector = selectors.DefaultSelector()
        self.selector.register(process.stdout, selectors.EVENT_READ)
        self.buffer = b""
        self.sequence = 0
        self.records = []

    def send(self, value):
        with (self.output / "requests.jsonl").open("a") as stream:
            stream.write(json.dumps(value, ensure_ascii=False) + "\n")
        self.process.stdin.write((json.dumps(value) + "\n").encode())
        self.process.stdin.flush()

    def read(self):
        while b"\n" not in self.buffer:
            remaining = self.deadline - time.monotonic()
            if remaining <= 0 or not self.selector.select(remaining):
                raise TimeoutError("대화·압축 검사 시간 제한 초과")
            chunk = os.read(self.process.stdout.fileno(), 65536)
            if not chunk:
                raise ValueError("압축 검사 서버가 기록 수집 전에 종료됨")
            self.buffer += chunk
        line, self.buffer = self.buffer.split(b"\n", 1)
        value = json.loads(line)
        self.records.append(value)
        with (self.output / "protocol.jsonl").open("a") as stream:
            stream.write(line.decode() + "\n")
        if "method" in value and "id" in value:
            # 승인·사용자 입력·추가 도구 요청을 자동 승인하지 않는다.
            self.send({"id": value["id"], "error": {"code": -32601, "message": "평가에서 추가 권한·도구 승인 불가"}})
            raise ValueError(f"예상하지 않은 서버 요청: {value['method']}")
        if value.get("method") == "error":
            raise ValueError("네이티브 실행 오류: " + json.dumps(value["params"]))
        return value

    def request(self, method, params):
        self.sequence += 1
        request_id = self.sequence
        self.send({"id": request_id, "method": method, "params": params})
        while True:
            value = self.read()
            if value.get("id") == request_id:
                if "error" in value:
                    raise ValueError(f"{method} 실패: {value['error']}")
                return value["result"]

    def wait_turn(self, thread_id, turn_id):
        while True:
            value = self.read()
            if value.get("method") == "turn/completed":
                params = value["params"]
                if params.get("threadId") != thread_id or params["turn"]["id"] != turn_id:
                    raise ValueError("다른 스레드·턴의 완료 이벤트")
                if params["turn"]["status"] != "completed":
                    raise ValueError("네이티브 턴 미완료: " + json.dumps(params["turn"]))
                return


def normalize(records, output):
    """원본 프로토콜은 보존하고 기존 판정기의 행동 기록 형식으로도 기록한다."""
    events = []
    for value in records:
        params = value.get("params", {})
        if value.get("method") == "item/completed":
            item = params.get("item", {})
            if item.get("type") == "agentMessage":
                events.append({"type": "item.completed", "item": {"type": "agent_message", "text": item["text"]}})
            elif item.get("type") == "commandExecution":
                events.append({"type": "item.completed", "item": {"type": "command_execution",
                    "command": item.get("command"), "aggregated_output": item.get("aggregatedOutput", ""),
                    "exit_code": item.get("exitCode")}})
        elif value.get("method") == "turn/completed" and params.get("turn", {}).get("status") == "completed":
            events.append({"type": "turn.completed", "usage": None})
        elif value.get("method") == "error":
            events.append({"type": "item.completed", "item": {"type": "error", "message": json.dumps(params)}})
    (output / "events.jsonl").write_text("".join(json.dumps(e, ensure_ascii=False) + "\n" for e in events))


def evidence(output):
    records = [json.loads(line) for line in (output / "protocol.jsonl").read_text().splitlines()]
    compact = [v["params"] for v in records if v.get("method") == "item/completed"
               and v.get("params", {}).get("item", {}).get("type") == "contextCompaction"]
    turns = [v["params"] for v in records if v.get("method") == "turn/completed"
             and v.get("params", {}).get("turn", {}).get("status") == "completed"]
    threads = {v["threadId"] for v in turns + compact}
    usage = [v["params"] for v in records if v.get("method") == "thread/tokenUsage/updated"]
    compact_ids = {v.get("turnId") for v in compact}
    first_compact = next((i for i, v in enumerate(records) if v.get("method") == "item/completed"
                          and v.get("params", {}).get("item", {}).get("type") == "contextCompaction"), len(records))
    resumed = [v["params"] for v in records[first_compact + 1:] if v.get("method") == "turn/completed"
               and v.get("params", {}).get("turn", {}).get("status") == "completed"
               and v["params"]["turn"]["id"] not in compact_ids]
    return {"completed_compactions": len(compact), "completed_turns": len(turns),
            "resumed_turns": len(resumed), "same_thread": len(threads) == 1, "token_usage": usage}


def execute(command, cwd, output, prompts, timeout, snapshot_callback):
    start = time.monotonic()
    with (output / "stderr.log").open("wb") as stderr:
        process = subprocess.Popen(command, cwd=cwd, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                   stderr=stderr, start_new_session=True)
        client = Protocol(process, output, start + timeout)
        try:
            client.request("initialize", {"clientInfo": {"name": "chalkak-harness-eval", "version": "1"},
                                           "capabilities": {"experimentalApi": True}})
            client.send({"method": "initialized", "params": {}})
            started = client.request("thread/start", {"cwd": str(cwd), "ephemeral": True, "approvalPolicy": "never"})
            if started["cwd"] != str(cwd) or started["approvalPolicy"] != "never":
                raise ValueError("네이티브 스레드의 작업 위치·승인 정책이 검사 설정과 다름")
            thread_id = started["thread"]["id"]
            (output / "thread-start.json").write_text(json.dumps(started, ensure_ascii=False, indent=2))
            for index, prompt in enumerate(prompts):
                if prompt == {"compact": True}:
                    client.request("thread/compact/start", {"threadId": thread_id})
                    while not evidence(output)["completed_compactions"]:
                        client.read()
                else:
                    result = client.request("turn/start", {"threadId": thread_id,
                        "input": [{"type": "text", "text": prompt}]})
                    client.wait_turn(thread_id, result["turn"]["id"])
                snapshot_callback(index)
            return 0, round(time.monotonic() - start, 2)
        finally:
            normalize(client.records, output)
            client.selector.close()
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGKILL)
            process.wait()
            process.stdin.close()
            process.stdout.close()
