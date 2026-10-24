import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("classify", Path(__file__).with_name("classify-release.py"))
classify = importlib.util.module_from_spec(spec)
spec.loader.exec_module(classify)


class ReleaseClassificationTest(unittest.TestCase):
    def test_push_ranges(self):
        with tempfile.TemporaryDirectory() as directory:
            previous = os.getcwd()
            os.chdir(directory)
            try:
                def git(*args):
                    return subprocess.check_output(["git", *args], text=True).strip()
                git("init", "-q")
                git("config", "user.email", "test@example.com")
                git("config", "user.name", "Test")
                def commit(message):
                    git("commit", "--allow-empty", "-qm", message)
                    return git("rev-parse", "HEAD")
                start = commit("release old version")
                end = commit("fix prerelease classification")
                self.assertTrue(classify.is_prerelease("push", {"before": start, "after": end}))
                stable = commit("fix identities\n\nReady for RELEASE.")
                tip = commit("followup")
                self.assertFalse(classify.is_prerelease("push", {"before": end, "after": tip}))
                self.assertTrue(classify.is_prerelease("push", {"before": stable, "after": tip}))
                self.assertTrue(classify.is_prerelease("workflow_dispatch", {}))
                self.assertFalse(classify.is_prerelease("push", {"before": "0" * 40, "after": tip}))
            finally:
                os.chdir(previous)


if __name__ == "__main__":
    unittest.main()
