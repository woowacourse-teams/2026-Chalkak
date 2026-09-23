"""Publication integration tests: all pushes target a disposable local bare repository."""
import os
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parent))
import manage
import publish
from test_manage import command, repository, commit


class PublishTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.source = self.root / "backend"
        repository(self.source)
        real = Path(__file__).resolve().parents[3]
        for name in (*manage.ENTRY, *manage.REFERENCES):
            manage.write(self.source / name, (real / name).read_bytes())
        for prefix in manage.PREFIXES:
            shutil.copytree(real / prefix, self.source / prefix)
        manage.write(self.source / "backend/app.txt", b"server implementation")
        manage.write(self.source / "docs/business-rules/obsolete.md", b"old reference")
        commit(self.source)
        command(self.source, "branch", "-m", "be/develop")
        self.remote = self.root / "origin.git"
        command(self.root, "init", "--bare", str(self.remote))
        command(self.remote, "config", "user.name", "Fixture")
        command(self.remote, "config", "user.email", "fixture@example.invalid")
        command(self.source, "remote", "add", "origin", str(self.remote))
        command(self.source, "push", "-u", "origin", "be/develop")

    def head(self, branch="harness/shared"):
        return command(self.remote, "rev-parse", "refs/heads/" + branch).decode().strip()

    def update(self):
        (self.source / "docs/business-rules/obsolete.md").unlink()
        manage.write(self.source / "docs/business-rules/new.md", b"new reference")
        commit(self.source)
        command(self.source, "push", "origin", "be/develop")

    def test_initial_branch_update_deletion_and_idempotent_retry(self):
        before = command(self.source, "status", "--porcelain")
        first = publish.publish(self.source)
        self.assertTrue(first["published"])
        self.assertEqual(before, command(self.source, "status", "--porcelain"))
        self.assertEqual(b"be/develop\n", command(self.source, "branch", "--show-current"))
        tree = command(self.remote, "ls-tree", "-r", "--name-only", "harness/shared").decode()
        self.assertNotIn("backend/app.txt", tree)
        self.assertIn(".agents/skills/business-rules/SKILL.md", tree)
        self.assertEqual(1, len(command(self.remote, "rev-list", "--parents", "-n", "1", "harness/shared").split()))
        self.assertFalse(publish.publish(self.source)["published"])
        self.assertEqual(first["release_commit"], self.head())
        self.update()
        second = publish.publish(self.source)
        self.assertTrue(second["published"])
        tree = command(self.remote, "ls-tree", "-r", "--name-only", "harness/shared").decode()
        self.assertNotIn("obsolete.md", tree)
        self.assertIn("new.md", tree)
        self.assertEqual(first["release_commit"], command(self.remote, "rev-parse", "harness/shared^").decode().strip())
        # Consume exactly the automatically published branch, using the real downloader/installer.
        cache = self.root / "cache"
        manage.sync(cache, str(self.remote), "harness/shared")
        client = self.root / "client"
        repository(client)
        manage.write(client / "client/android/AGENTS.md", b"Android instructions")
        commit(client)
        manage.activate(client, cache)
        self.assertTrue((client / ".agents/skills/business-rules/SKILL.md").is_file())
        self.assertEqual(second["version"], manage.status(client)["installed"])
        self.assertEqual(b"Android instructions", (client / "client/android/AGENTS.md").read_bytes())

    def test_older_job_cannot_replace_newer_publication(self):
        old_source = command(self.source, "rev-parse", "HEAD").decode().strip()
        publish.publish(self.source)
        self.update()
        newest = publish.publish(self.source)
        command(self.source, "checkout", "--detach", old_source)
        result = publish.publish(self.source)
        self.assertFalse(result["published"])
        self.assertIn("최신", result["reason"])
        self.assertEqual(newest["release_commit"], self.head())

    def test_validation_route_updates_only_validation_branch(self):
        production = publish.publish(self.source)
        command(self.source, "checkout", "-b", publish.VALIDATION_SOURCE_BRANCH)
        probe = self.source / "docs/business-rules/distribution-probe.md"
        for content in (b"distribution probe v1", b"distribution probe v2", None):
            if content is None:
                probe.unlink()
            else:
                manage.write(probe, content)
            commit(self.source)
            command(self.source, "push", "origin", publish.VALIDATION_SOURCE_BRANCH)
            result = publish.publish(self.source, validation=True)
            self.assertTrue(result["published"])
            self.assertEqual(publish.VALIDATION_BRANCH, result["branch"])
            self.assertEqual(production["release_commit"], self.head())
            cache = self.root / "validation-cache"
            manage.sync(cache, str(self.remote), publish.VALIDATION_BRANCH)
            release = cache / "versions" / result["version"]
            manifest = manage.verify(release)
            self.assertEqual(result["source_commit"], manifest["source_commit"])
            published_probe = release / probe.relative_to(self.source)
            if content is None:
                self.assertFalse(published_probe.exists())
            else:
                self.assertEqual(content, published_probe.read_bytes())
        # A validation-only source cannot be sent through the production route.
        with self.assertRaisesRegex(ValueError, "be/develop에 포함되지 않은"):
            publish.publish(self.source)

    def test_validation_route_rejects_unpushed_source(self):
        command(self.source, "checkout", "-b", publish.VALIDATION_SOURCE_BRANCH)
        command(self.source, "push", "origin", publish.VALIDATION_SOURCE_BRANCH)
        manage.write(self.source / "docs/business-rules/unpushed.md", b"not on origin")
        commit(self.source)
        with self.assertRaisesRegex(ValueError, "포함되지 않은"):
            publish.publish(self.source, validation=True)
        self.assertEqual(b"", command(self.remote, "for-each-ref", "refs/heads/harness/"))

    def test_unmerged_source_cannot_be_published(self):
        manage.write(self.source / "docs/business-rules/unmerged.md", b"proposal")
        commit(self.source)
        with self.assertRaisesRegex(ValueError, "포함되지 않은"):
            publish.publish(self.source)
        self.assertEqual(b"", command(self.remote, "for-each-ref", "refs/heads/harness/shared"))

    def test_validation_failure_preserves_last_release(self):
        first = publish.publish(self.source)
        (self.source / "docs/business-rules/NOTION_SYNC.md").unlink()
        commit(self.source)
        command(self.source, "push", "origin", "be/develop")
        with self.assertRaisesRegex(ValueError, "로컬 문서 참조"):
            publish.publish(self.source)
        self.assertEqual(first["release_commit"], self.head())

    def test_rejected_push_preserves_last_release(self):
        first = publish.publish(self.source)
        self.update()
        hook = self.remote / "hooks/pre-receive"
        hook.write_text("#!/bin/sh\nexit 1\n")
        hook.chmod(0o755)
        with self.assertRaisesRegex(ValueError, "게시 Git 명령 실패"):
            publish.publish(self.source)
        self.assertEqual(first["release_commit"], self.head())

    def test_concurrent_destination_change_is_not_overwritten(self):
        first = publish.publish(self.source)
        self.update()
        real_git = publish.git
        concurrent = []
        def race(source, *args, **kwargs):
            if args[0] == "push":
                tree = command(self.remote, "rev-parse", "harness/shared^{tree}").decode().strip()
                new = command(self.remote, "-c", "commit.gpgsign=false", "commit-tree", tree,
                              "-p", first["release_commit"], "-m", "another publisher").decode().strip()
                command(self.remote, "update-ref", "refs/heads/harness/shared", new)
                concurrent.append(new)
            return real_git(source, *args, **kwargs)
        with patch.object(publish, "git", side_effect=race):
            with self.assertRaisesRegex(ValueError, "게시 Git 명령 실패"):
                publish.publish(self.source)
        self.assertEqual(concurrent[0], self.head())
