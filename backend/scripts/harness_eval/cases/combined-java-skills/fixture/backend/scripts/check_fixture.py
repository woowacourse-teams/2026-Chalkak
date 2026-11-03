"""실제 프로젝트 JavaLayout, javac, JUnit 검사. 오프라인 작업 자료만 사용한다."""
from pathlib import Path
import importlib.util
import sys

backend = Path(__file__).resolve().parents[1]
if sys.argv[1:] != ["test"]:
    sys.exit("사용법: python3 scripts/check_fixture.py test")
runner = backend.parent / ".eval/runtime/java_fixture.py"
spec = importlib.util.spec_from_file_location("java_fixture_runtime", runner)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
sys.exit(module.check(backend))
