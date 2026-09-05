#!/usr/bin/env python3
"""Regression tests for the repository's deterministic harness checks."""

from contextlib import contextmanager
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

if __package__:
    from . import check_harness
else:
    import check_harness


class CheckHarnessTest(unittest.TestCase):
    @contextmanager
    def repository(self):
        with tempfile.TemporaryDirectory(prefix="check-harness-test-") as temporary:
            root = Path(temporary) / "backend"
            root.mkdir()
            for name in ("main-code", "test-code", "demo"):
                self.write(root, f".agents/skills/{name}/SKILL.md", self.skill(name))
            self.write(root, ".claude/skills/demo/SKILL.md", self.skill("demo"))
            for name, source in (("main-code", "main"), ("test-code", "test")):
                self.write(
                    root,
                    f".claude/rules/{name}.md",
                    f'---\npaths:\n  - "src/{source}/java/**/*.java"\n---\n# Rules\n',
                )
            self.write(root, "AGENTS.md", "Use `$demo`, `$main-code`, and `$test-code`.\nEnvironment: `$CODEX_HOME`.\n")
            self.write(
                root,
                "CLAUDE.md",
                "Use `demo` Skill and `.claude/rules/main-code.md` "
                "and `.claude/rules/test-code.md`.\n",
            )
            yield root

    @staticmethod
    def write(root, relative, content):
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        return path

    @staticmethod
    def skill(name, description="Use for a small example.", body="# Example\n"):
        return f"---\nname: {name}\ndescription: {description}\n---\n{body}"

    def shared_repository(self, root):
        repository = root.parent
        for platform in (".agents", ".claude"):
            self.write(repository, f"{platform}/skills/business-rules/SKILL.md", self.skill(
                "business-rules",
                body="# Rules\n[Business rules](../../../docs/business-rules/README.md)\n",
            ))
        self.write(repository, "AGENTS.md", "Use `$business-rules`.\n")
        self.write(repository, "CLAUDE.md", "Use `business-rules` Skill.\n")
        self.write(repository, "docs/business-rules/README.md",
                   "# Rules\n[예시](rules/example.md)\n")
        self.write(repository, "docs/business-rules/rules/example.md",
                   "<!-- business-rule-status: active -->\n## EXAMPLE-001 규칙\n"
                   "- 규칙: 현재 규칙\n- 적용 범위: 예시\n- 예외: 없음\n- 결정 기록: 없음\n")
        self.write(repository, "docs/business-rules/notion-map.yml",
                   "version: 2\nmode: manual\nlocation: 팀 규칙\n"
                   "rule_pages:\n  docs/business-rules/rules/example.md:\n"
                   "    domain: 예시\n    pages:\n      예시 페이지: [EXAMPLE-001]\n")
        return repository

    def assert_error_for(self, errors, path):
        self.assertTrue(errors, f"Expected a validation error for {path}")
        self.assertTrue(any(path in error for error in errors), errors)

    @staticmethod
    def run_cli(root):
        return subprocess.run(
            [sys.executable, str(Path(check_harness.__file__).resolve()), "--root", str(root)],
            cwd=root.parent,
            capture_output=True,
            text=True,
            check=False,
        )

    def test_valid_repository_is_deterministic_and_unchanged(self):
        with self.repository() as root:
            before = {path.relative_to(root): path.read_bytes() for path in root.rglob("*") if path.is_file()}

            first = check_harness.check(root)
            second = check_harness.check(root)

            self.assertEqual(([], []), first)
            self.assertEqual(first, second)
            after = {path.relative_to(root): path.read_bytes() for path in root.rglob("*") if path.is_file()}
            self.assertEqual(before, after)

    def test_platform_wording_and_valid_yaml_forms_may_differ(self):
        with self.repository() as root:
            self.write(
                root,
                ".agents/skills/demo/SKILL.md",
                '---\nname: "demo"\ndescription: "Codex: use # this example."\n'
                '---\n# Codex wording\nUse the Codex tool.\n',
            )
            self.write(
                root,
                ".claude/skills/demo/SKILL.md",
                "---\nname: 'demo'\ndescription: >-\n"
                "  Claude uses this example\n  when applicable.\n"
                "---\n# Claude wording\nUse a different tool.\n",
            )
            self.write(
                root,
                ".claude/rules/main-code.md",
                '---\npaths: ["src/main/java/**/*.java"]\n---\nDifferent rule wording.\n',
            )

            self.assertEqual(([], []), check_harness.check(root))

    def test_platform_specific_skill_reference_syntax(self):
        cases = (
            (".agents/skills/demo/SKILL.md", "\nUse `demo` Skill.\n"),
            ("AGENTS.md", "\n`demo` Skill도 사용한다.\n"),
            (".claude/skills/demo/SKILL.md", "\nUse `$demo`.\n"),
            ("CLAUDE.md", "\nUse `$demo`.\n"),
        )
        for relative, suffix in cases:
            with self.subTest(relative=relative), self.repository() as root:
                path = root / relative
                path.write_text(path.read_text(encoding="utf-8") + suffix, encoding="utf-8")

                errors, _ = check_harness.check(root)

                self.assert_error_for(errors, relative)

        with self.repository() as root:
            self.write(root, "CLAUDE.md", "`missing-skill` Skill이 필요하다.\n")
            errors, _ = check_harness.check(root)
            self.assert_error_for(errors, "CLAUDE.md")

    def test_malformed_yaml_and_duplicate_keys_fail(self):
        cases = (
            (".agents/skills/demo/SKILL.md", "name: [demo\ndescription: Example.\n"),
            (".claude/skills/demo/SKILL.md", "name: demo\nname: demo\ndescription: Example.\n"),
            (".agents/skills/demo/SKILL.md", "name: demo\ndescription: One.\ndescription: Two.\n"),
            (".claude/rules/main-code.md", 'paths: ["src/main/java/**/*.java"\n'),
            (".claude/rules/main-code.md", 'paths: []\npaths: ["src/main/java/**/*.java"]\n'),
        )
        for relative, frontmatter in cases:
            with self.subTest(relative=relative, frontmatter=frontmatter), self.repository() as root:
                self.write(root, relative, f"---\n{frontmatter}---\n# Body\n")

                errors, _ = check_harness.check(root)

                self.assert_error_for(errors, relative)

    def test_skill_metadata_requires_name_and_valid_description(self):
        frontmatters = (
            "description: Example.\n",
            "name: ''\ndescription: Example.\n",
            "name: 123\ndescription: Example.\n",
            "name: [demo]\ndescription: Example.\n",
            "name: demo\n",
            "name: demo\ndescription: ''\n",
            "name: demo\ndescription: '   '\n",
            "name: demo\ndescription: [Example]\n",
            "name: demo\ndescription: 123\n",
            "name: demo\ndescription: " + "x" * 1025 + "\n",
        )
        for platform in (".agents", ".claude"):
            relative = f"{platform}/skills/demo/SKILL.md"
            for frontmatter in frontmatters:
                with self.subTest(platform=platform, frontmatter=frontmatter), self.repository() as root:
                    self.write(root, relative, f"---\n{frontmatter}---\n# Body\n")

                    errors, _ = check_harness.check(root)

                    self.assert_error_for(errors, relative)

            with self.subTest(platform=platform, description_length=1024), self.repository() as root:
                self.write(root, relative, self.skill("demo", description="x" * 1024))

                self.assertEqual(([], []), check_harness.check(root))

    def test_skill_names_match_directories_and_team_format(self):
        for name in ("Demo", "demo_name", "데모", "a" * 65):
            with self.subTest(name=name), self.repository() as root:
                self.write(root, "AGENTS.md", "# Instructions\n")
                self.write(root, "CLAUDE.md", "# Instructions\n")
                for platform in (".agents", ".claude"):
                    (root / platform / "skills/demo").rename(root / platform / f"skills/{name}")
                    self.write(root, f"{platform}/skills/{name}/SKILL.md", self.skill(name))

                errors, _ = check_harness.check(root)

                self.assert_error_for(errors, f"skills/{name}/SKILL.md")

        with self.repository() as root:
            relative = ".agents/skills/demo/SKILL.md"
            self.write(root, relative, self.skill("different-name"))

            errors, _ = check_harness.check(root)

            self.assert_error_for(errors, relative)

    def test_relative_links_resolve_from_the_document_directory(self):
        documents = (
            ".agents/skills/demo/SKILL.md",
            ".claude/skills/demo/SKILL.md",
            ".claude/rules/main-code.md",
            "AGENTS.md",
            "CLAUDE.md",
        )
        links = (
            "\n[Example](references/example.md#example)\n"
            "[Parentheses](references/example(v2).md)\n"
            "[Spaces](<references/example notes.md>)\n"
            "[Reference][notes]\n\n[notes]: references/reference.md\n\n"
            "`[Inline code](references/missing-inline.md)`\n\n"
            "```markdown\n[Fenced code](references/missing-fenced.md)\n```\n"
            "[Web](https://example.invalid/not-checked)\n"
        )
        for relative in documents:
            with self.subTest(relative=relative), self.repository() as root:
                target = root / relative
                references = target.parent / "references"
                references.mkdir()
                filenames = ("example.md", "example(v2).md", "example notes.md", "reference.md")
                for filename in filenames:
                    (references / filename).write_text("# Example\n", encoding="utf-8")
                target.write_text(target.read_text(encoding="utf-8") + links, encoding="utf-8")
                self.assertEqual(([], []), check_harness.check(root))

                for filename in filenames:
                    with self.subTest(missing=filename):
                        reference = references / filename
                        reference.unlink()
                        errors, _ = check_harness.check(root)

                        self.assert_error_for(errors, relative)
                        reference.write_text("# Example\n", encoding="utf-8")

    def test_root_references_require_existing_skills_and_rules(self):
        cases = (
            ("AGENTS.md", "Use `$missing-skill`.\n"),
            ("AGENTS.md", "Use `$missing_skill`.\n"),
            ("AGENTS.md", "Use `$Missing-Skill`.\n"),
            ("CLAUDE.md", "Use `missing-skill` Skill.\n"),
            ("CLAUDE.md", "Use `missing_skill` Skill.\n"),
            ("CLAUDE.md", "Use `missing.skill` Skill.\n"),
            ("CLAUDE.md", "Use `.claude/rules/missing-rule.md`.\n"),
        )
        for relative, body in cases:
            with self.subTest(relative=relative, body=body), self.repository() as root:
                self.write(root, relative, body)

                errors, _ = check_harness.check(root)

                self.assert_error_for(errors, relative)

    def test_platform_pairs_are_required_in_both_directions(self):
        cases = (
            (".agents/skills/demo/SKILL.md", "demo"),
            (".claude/skills/demo/SKILL.md", "demo"),
            (".agents/skills/main-code/SKILL.md", "main-code"),
            (".claude/rules/test-code.md", "test-code"),
        )
        for relative, name in cases:
            with self.subTest(relative=relative), self.repository() as root:
                self.write(root, "AGENTS.md", "# Instructions\n")
                self.write(root, "CLAUDE.md", "# Instructions\n")
                (root / relative).unlink()

                errors, _ = check_harness.check(root)

                self.assert_error_for(errors, name)

    def test_rule_paths_are_string_lists_with_the_expected_scope(self):
        for name in ("main-code", "test-code"):
            relative = f".claude/rules/{name}.md"
            for frontmatter in (
                "description: No paths.\n",
                "paths: src/main/java/**/*.java\n",
                "paths: []\n",
                "paths: [123]\n",
                'paths: ["src/other/java/**/*.java"]\n',
            ):
                with self.subTest(name=name, frontmatter=frontmatter), self.repository() as root:
                    self.write(root, relative, f"---\n{frontmatter}---\n# Rules\n")

                    errors, _ = check_harness.check(root)

                    self.assert_error_for(errors, relative)

    def test_body_length_is_a_warning_starting_at_500_lines(self):
        for platform in (".agents", ".claude"):
            relative = f"{platform}/skills/demo/SKILL.md"
            with self.subTest(platform=platform), self.repository() as root:
                self.write(root, relative, self.skill("demo", body="Body line.\n" * 499))
                self.assertEqual(([], []), check_harness.check(root))

                self.write(root, relative, self.skill("demo", body="Body line.\n" * 500))
                errors, warnings = check_harness.check(root)

                self.assertEqual([], errors)
                self.assertTrue(any(relative in warning for warning in warnings), warnings)

    def test_cli_succeeds_for_valid_and_warning_only_repositories(self):
        with self.repository() as root:
            result = self.run_cli(root)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)

            self.write(root, ".agents/skills/demo/SKILL.md", self.skill("demo", body="Body.\n" * 500))
            result = self.run_cli(root)

            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn(".agents/skills/demo/SKILL.md", result.stdout + result.stderr)

    def test_cli_checks_shared_repository_skills_and_business_rule_links(self):
        with self.repository() as root:
            repository = self.shared_repository(root)
            readme = repository / "docs/business-rules/README.md"

            self.assertEqual(0, self.run_cli(root).returncode)

            readme.write_text("# Rules\n[Missing](rules/missing.md)\n", encoding="utf-8")
            result = self.run_cli(root)

            self.assertEqual(1, result.returncode, result.stdout + result.stderr)
            self.assertIn("../docs/business-rules/README.md", result.stdout + result.stderr)

    def test_cli_does_not_require_shared_harness_for_parent_git_directory(self):
        with self.repository() as root:
            (root.parent / ".git").mkdir()

            self.assertEqual(0, self.run_cli(root).returncode)

    def test_cli_fails_for_invalid_repository(self):
        with self.repository() as root:
            self.write(root, "AGENTS.md", "Use `$missing-skill`.\n")
            result = self.run_cli(root)
            self.assertEqual(1, result.returncode)
            self.assertIn("AGENTS.md", result.stderr)

    def test_manual_map_rejects_missing_duplicate_and_unknown_rules(self):
        with self.repository() as root:
            repository = self.shared_repository(root)
            path = repository / "docs/business-rules/notion-map.yml"
            valid = path.read_text()
            self.assertEqual(0, self.run_cli(root).returncode)
            for text in (valid.replace('[EXAMPLE-001]', '[OTHER-001]'),
                         valid.replace('[EXAMPLE-001]', '[EXAMPLE-001, EXAMPLE-001]'),
                         valid.replace('example.md:', 'missing.md:'),
                         valid.replace('mode: manual', 'mode: automatic'),
                         valid + 'version: 2\n',
                         valid + 'root_page: old-target\n'):
                with self.subTest(text=text):
                    path.write_text(text)
                    self.assertEqual(1, self.run_cli(root).returncode)

    def test_manual_map_requires_new_rule_location(self):
        with self.repository() as root:
            repository = self.shared_repository(root)
            self.write(repository, "docs/business-rules/rules/new.md",
                       "<!-- business-rule-status: active -->\n## NEW-001 새 규칙\n"
                       "- 규칙: 새 규칙\n- 적용 범위: 예시\n- 예외: 없음\n- 결정 기록: 없음\n")
            readme = repository / "docs/business-rules/README.md"
            readme.write_text(readme.read_text() + "[새 규칙](rules/new.md)\n")
            result = self.run_cli(root)
            self.assertEqual(1, result.returncode)
            self.assertIn("rule_pages에 활성 규칙 파일이 없습니다", result.stderr)

    def test_active_rules_require_fields_and_readme_entry(self):
        with self.repository() as root:
            repository = self.shared_repository(root)
            rule = repository / "docs/business-rules/rules/example.md"
            valid = rule.read_text()

            rule.write_text(valid.replace("- 예외: 없음\n", ""))
            result = self.run_cli(root)
            self.assertEqual(1, result.returncode)
            self.assertIn("EXAMPLE-001의 예외", result.stderr)

            rule.write_text(valid)
            (repository / "docs/business-rules/README.md").write_text("# Rules\n")
            result = self.run_cli(root)
            self.assertEqual(1, result.returncode)
            self.assertIn("활성 규칙 문서가 목차에 없습니다", result.stderr)

    def test_decision_link_exists_and_references_rule(self):
        with self.repository() as root:
            repository = self.shared_repository(root)
            rule = repository / "docs/business-rules/rules/example.md"
            rule.write_text(rule.read_text().replace(
                "- 결정 기록: 없음",
                "- 결정 기록: [변경 이유](../decisions/2026-09-05-example.md)",
            ))
            decision = self.write(repository, "docs/business-rules/decisions/2026-09-05-example.md",
                                  "# 변경 이유\n- 관련 규칙: OTHER-001\n")

            result = self.run_cli(root)
            self.assertEqual(1, result.returncode)
            self.assertIn("해당 ID가 없습니다: EXAMPLE-001", result.stderr)

            decision.write_text("# 변경 이유\n- 관련 규칙: EXAMPLE-001\n")
            self.assertEqual(0, self.run_cli(root).returncode)


if __name__ == "__main__":
    unittest.main()
