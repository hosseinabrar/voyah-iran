import tempfile,unittest,json,threading
from pathlib import Path
from unittest.mock import patch
from project_sync import ProjectSync,digest

class SyncTests(unittest.TestCase):
    def test_matching_snapshot_skips_export(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)/'experiments/web';root.mkdir(parents=True)
            checkpoint=root/'feedback';checkpoint.write_bytes(b'feedback')
            sync=ProjectSync(root,checkpoint)
            with patch.object(sync,'installed',return_value=digest(checkpoint)),patch.object(sync,'run_script') as run:
                sync.sync_once();run.assert_not_called()
            self.assertEqual(sync.status()['state'],'synced')

    def test_new_feedback_during_export_never_publishes_stale_snapshot(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)/'experiments/web';root.mkdir(parents=True)
            checkpoint=root/'feedback';checkpoint.write_bytes(b'old')
            sync=ProjectSync(root,checkpoint);calls=[]
            def run(name,env,log,*args):
                calls.append(name)
                if name=='verify_android_export.py':checkpoint.write_bytes(b'new')
            with patch.object(sync,'installed',return_value=None),patch.object(sync,'run_script',side_effect=run):
                sync.sync_once()
            self.assertNotIn('install_android_assets.py',calls)
            self.assertTrue(sync.event.is_set())
            self.assertEqual(sync.status()['state'],'pending')

    def test_failed_verification_does_not_install(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)/'experiments/web';root.mkdir(parents=True)
            checkpoint=root/'feedback';checkpoint.write_bytes(b'new')
            sync=ProjectSync(root,checkpoint);calls=[]
            def run(name,*args):
                calls.append(name)
                if name=='verify_android_export.py':raise RuntimeError('parity failed')
            with patch.object(sync,'installed',return_value=None),patch.object(sync,'run_script',side_effect=run):
                with self.assertRaises(RuntimeError):sync.sync_once()
            self.assertNotIn('install_android_assets.py',calls)

if __name__=='__main__':unittest.main()
