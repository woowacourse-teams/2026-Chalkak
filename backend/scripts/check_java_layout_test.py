"""Java AST 줄바꿈 검사의 정상·오류 주입 회귀 검사. AI·DB·네트워크 호출 없음."""

from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


BACKEND = Path(__file__).resolve().parents[1]


class JavaLayoutTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workspace = tempfile.TemporaryDirectory(prefix="java-layout-test-")
        cls.addClassCleanup(cls.workspace.cleanup)
        cls.root = Path(cls.workspace.name)
        cls.java = shutil.which("java")
        javac = shutil.which("javac")
        if not cls.java or not javac:
            raise RuntimeError("Java layout regression tests require a JDK")
        subprocess.run(
            [javac, "-encoding", "UTF-8", "-d", str(cls.root), str(BACKEND / "scripts/CheckJavaLayout.java")],
            check=True, capture_output=True, text=True,
        )

    def check_source(self, source, valid, folder="src/main/java"):
        with tempfile.TemporaryDirectory(dir=self.root) as temporary:
            path = Path(temporary, folder, "Probe.java")
            path.parent.mkdir(parents=True)
            path.write_text(textwrap.dedent(source), encoding="utf-8")
            before = path.read_bytes()
            result = subprocess.run(
                [self.java, "-cp", str(self.root), "CheckJavaLayout", str(path.parent)],
                capture_output=True, text=True,
            )
            self.assertEqual(result.returncode == 0, valid, result.stdout + result.stderr)
            self.assertEqual(path.read_bytes(), before, "검사기가 소스를 수정하면 안 됨")
            if not valid:
                self.assertIn(str(path), result.stderr)
            return result

    def test_zero_one_two_items_are_not_forced_to_wrap(self):
        self.check_source("""
            record Pair(int first, int second) {}
            class Probe {
                Probe() { this(1, 2); }
                Probe(int first, int second) { super(first, second); }
                void run(int first, int second) {
                    zero(); one(1); two(1, 2); new Pair(1, 2);
                }
            }
        """, True)

    def test_three_and_four_items_require_wrapping(self):
        for folder in ("src/main/java", "src/test/java"):
            for count in (3, 4):
                items = ", ".join(str(i) for i in range(count))
                declarations = ", ".join(f"int value{i}" for i in range(count))
                for source in (
                    f"class Probe {{ void run() {{ call({items}); }} }}",
                    f"class Probe {{ void run() {{ new Result({items}); }} }}",
                    f"class Probe {{ void run({declarations}) {{}} }}",
                    f"class Probe {{ Probe({declarations}) {{}} }}",
                    f"record Probe({declarations}) {{}}",
                    f"enum Probe {{ ITEM({items}); }}",
                    f"class Probe {{ Probe() {{ this({items}); }} }}",
                    f"class Probe {{ Probe() {{ super({items}); }} }}",
                ):
                    with self.subTest(folder=folder, count=count, source=source):
                        result = self.check_source(source, False, folder)
                        self.assertIn("[JavaLayout]", result.stderr)

    def test_correctly_wrapped_lists_pass(self):
        self.check_source("""
            record Probe<T>(
                java.util.Map<String, T> first,
                T second,
                T third
            ) {
                static final Object CONSTANT = null;
                Probe { validate(first, second); }
                void call(
                    int first,
                    int second,
                    int third
                ) {
                    new Result(
                        first,
                        nested(1, 2),
                        third
                    );
                }
            }
            enum Example {
                ITEM(
                    1,
                    2,
                    3
                ) {};
            }
            class Child extends Parent {
                Child() {
                    this(
                        1,
                        2,
                        3
                    );
                }
                Child(
                    int first,
                    int second,
                    int third
                ) {
                    super(
                        first,
                        second,
                        third
                    );
                }
            }
        """, True)

    def test_first_item_each_item_and_closing_parenthesis_are_checked(self):
        for arguments in ("1,\n    2,\n    3\n)",
                          "\n    1, 2,\n    3\n)",
                          "\n    1,\n    2,\n    3)"):
            with self.subTest(arguments=arguments):
                self.check_source("class Probe { void run() { call(" + arguments + "; } }", False)

    def test_nested_call_and_anonymous_class_are_visited(self):
        self.check_source("""
            class Probe {
                void run() {
                    outer(
                        first,
                        inner(1, 2, 3),
                        new Result(1, 2, 3) {}
                    );
                }
            }
        """, False)

    def test_generics_annotations_arrays_and_lambda_parameters_do_not_inflate_count(self):
        self.check_source("""
            @Example(first = 1, second = 2, third = 3)
            class Probe {
                int[] array = {1, 2, 3};
                Object lambda = (first, second, third) -> first;
                void run() {
                    call(new java.util.HashMap<String, Integer>(), new int[] {1, 2, 3});
                }
            }
        """, True)

    def test_parentheses_and_commas_inside_literals_and_comments_are_ignored(self):
        self.check_source('''
            class Probe {
                void run() {
                    call(
                        "a, b, c ) \\\"",
                        ')',
                        """
                        (comma, comma) and escaped triple quote: \\"""
                        """ /* ) comma, comma */
                    );
                }
            }
        ''', True)

    def test_parse_errors_fail_instead_of_silently_skipping_file(self):
        self.check_source("class Probe { void run( {", False)

    def test_missing_or_empty_sources_fail(self):
        for path in (self.root / "missing", self.root / "empty"):
            if path.name == "empty":
                path.mkdir()
            result = subprocess.run(
                [self.java, "-cp", str(self.root), "CheckJavaLayout", str(path)],
                capture_output=True, text=True,
            )
            self.assertNotEqual(result.returncode, 0)


if __name__ == "__main__":
    unittest.main()
