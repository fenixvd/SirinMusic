"""Проверки условий, при которых CI допускает публикацию версии."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from importlib.util import module_from_spec, spec_from_file_location

SCRIPT = Path(__file__).with_name('release-version.py')
SPEC = spec_from_file_location('release_version', SCRIPT)
VERSION = module_from_spec(SPEC)
SPEC.loader.exec_module(VERSION)


class ReleaseVersionTest(unittest.TestCase):
    def parse(self, source):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'build.gradle.kts'
            path.write_text(source)
            return VERSION.read_version(path)

    def test_literal_version_is_read_from_source(self):
        self.assertEqual(
            self.parse('versionName = "9.8.7"\nversionCode = 42\n'),
            {'versionName': '9.8.7', 'versionCode': 42},
        )

    def test_dynamic_or_ambiguous_code_is_rejected(self):
        for code in ['providers.environmentVariable("CODE").get().toInt()',
                     '42\nversionCode = 43', '0', '-1']:
            with self.subTest(code=code), self.assertRaises(ValueError):
                self.parse('versionName = "9.8.7"\nversionCode = ' + code + '\n')

    def test_missing_or_duplicate_name_is_rejected(self):
        for source in ['versionCode = 42\n',
                       'versionCode = 42\nversionName = "9.8.7"\nversionName = "9.8.8"\n']:
            with self.subTest(source=source), self.assertRaises(ValueError):
                self.parse(source)

    def test_wrong_tag_stops_release(self):
        result = subprocess.run([sys.executable, str(SCRIPT), '--tag', 'v0.0.0-invalid'], capture_output=True)
        self.assertNotEqual(result.returncode, 0)

    def test_rerun_number_and_old_environment_cannot_change_version(self):
        version = VERSION.read_version(SCRIPT.parent.parent / 'app/build.gradle.kts')
        for number in ['1', '99999']:
            env = dict(os.environ, GITHUB_RUN_NUMBER=number,
                       SIRIN_VERSION_CODE='99999', SIRIN_VERSION_NAME='0.0.0')
            result = subprocess.run([sys.executable, str(SCRIPT), '--tag', 'v' + version['versionName']],
                                    capture_output=True, text=True, check=True, env=env)
            self.assertEqual(json.loads(result.stdout), version)


if __name__ == '__main__':
    unittest.main()
