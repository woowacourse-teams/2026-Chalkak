"""Exercise the released shell installer against a local download stand-in."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

import package_installer as pack
from test_manage import repository, commit, command


class InstallerTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.source = self.root / "source"
        repository(self.source)
        self.files = self.source / "backend/scripts/shared_harness"
        self.files.mkdir(parents=True)
        shutil.copyfile(Path(__file__).with_name("install.sh"), self.files / "install.sh")
        (self.files / "manage.py").write_text('import os\nfrom pathlib import Path\nPath(os.environ["PROBE_RESULT"]).write_text("reviewed")\n')
        (self.files / "automatic.py").write_text('# reviewed helper\n')
        commit(self.source)
        self.sha = command(self.source, "rev-parse", "HEAD").decode().strip()
        self.installer = self.root / "install.sh"
        pack.package(self.source, self.sha, self.installer)
        self.downloads = self.root / "downloads"
        shutil.copytree(self.files, self.downloads)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        (self.bin / "uname").write_text('#!/bin/sh\necho Darwin\n')
        (self.bin / "curl").write_text('''#!/usr/bin/env python3
import os, sys, shutil
from pathlib import Path
url = sys.argv[-3]
assert "/" + os.environ["EXPECTED_COMMIT"] + "/" in url
shutil.copyfile(Path(os.environ["PROBE_DOWNLOADS"]) / url.rsplit("/", 1)[1], sys.argv[-1])
''')
        for path in self.bin.iterdir():
            path.chmod(0o755)
        self.result = self.root / "result"
        self.env = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ["PATH"],
                        EXPECTED_COMMIT=self.sha, PROBE_DOWNLOADS=str(self.downloads),
                        PROBE_RESULT=str(self.result))

    def run_installer(self, path=None):
        return subprocess.run(["bash", str(path or self.installer)], cwd=self.source,
                              env=self.env, text=True, capture_output=True)

    def test_downloads_pinned_commit_and_ignores_neighboring_scripts(self):
        (self.root / "manage.py").write_text('raise RuntimeError("unreviewed neighbor")')
        (self.root / "automatic.py").write_text('raise RuntimeError("unreviewed neighbor")')
        (self.files / "manage.py").write_text('raise RuntimeError("new branch head")')
        commit(self.source)
        process = self.run_installer()
        self.assertEqual(0, process.returncode, process.stderr)
        self.assertEqual("reviewed", self.result.read_text())
        self.assertIn(self.sha, process.stdout)

    def test_either_hash_mismatch_stops_before_execution(self):
        for name in ("manage.py", "automatic.py"):
            with self.subTest(file=name):
                path = self.downloads / name
                old = path.read_bytes()
                path.write_bytes(old + b"# modified\n")
                process = self.run_installer()
                self.assertNotEqual(0, process.returncode)
                self.assertIn("검증 실패", process.stderr)
                self.assertFalse(self.result.exists())
                path.write_bytes(old)

    def test_template_cannot_install_without_release_metadata(self):
        process = self.run_installer(self.files / "install.sh")
        self.assertNotEqual(0, process.returncode)
        self.assertIn("배포용 설치기가 아닙니다", process.stderr)
        self.assertFalse(self.result.exists())

    def test_packaging_uses_committed_bytes_not_working_files(self):
        original = (self.files / "manage.py").read_bytes()
        (self.files / "manage.py").write_text("uncommitted")
        output = self.root / "second.sh"
        pack.package(self.source, self.sha, output)
        self.assertIn(hashlib.sha256(original).hexdigest(), output.read_text())
        self.assertEqual(self.installer.read_bytes(), output.read_bytes())

    def test_requires_explicit_commit_and_preserves_existing_output(self):
        with self.assertRaises(ValueError):
            pack.package(self.source, "HEAD", self.root / "bad.sh")
        before = self.installer.read_bytes()
        with self.assertRaises(FileExistsError):
            pack.package(self.source, self.sha, self.installer)
        self.assertEqual(before, self.installer.read_bytes())


if __name__ == "__main__":
    unittest.main()
