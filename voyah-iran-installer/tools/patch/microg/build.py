"""Rebuild checked-in small runtime artifacts: python3 build.py SDK BUILD_TOOLS_VERSION."""
import pathlib, subprocess, sys, tempfile, zipfile
root=pathlib.Path(__file__).resolve().parent
sdk=pathlib.Path(sys.argv[1]); tools=sdk/'build-tools'/sys.argv[2]
android=sorted((sdk/'platforms').glob('android-*/android.jar'))[-1]
with tempfile.TemporaryDirectory() as tmp:
    tmp=pathlib.Path(tmp)
    subprocess.run(['javac','-source','8','-target','8','-cp',str(android),'-d',str(tmp),str(root/'VoyahMicrogCompat.java'),str(root/'MicrogProvision.java')],check=True)
    for name, cls in [('helper','com/android/server/pm/VoyahMicrogCompat.class'),('provision','MicrogProvision.class')]:
        out=tmp/name;out.mkdir()
        subprocess.run([str(tools/'d8'),'--min-api','28','--output',str(out),str(tmp/cls)],check=True)
        if name=='provision': (root/'provision.dex').write_bytes((out/'classes.dex').read_bytes())
        else:
            jar=tmp/'helper.jar'
            with zipfile.ZipFile(jar,'w') as z:z.write(out/'classes.dex','classes.dex')
            subprocess.run(['java','-jar',str(root.parent/'apktool.jar'),'d','-f',str(jar),'-o',str(tmp/'decoded')],check=True)
            (root/'VoyahMicrogCompat.smali').write_bytes((tmp/'decoded/smali/com/android/server/pm/VoyahMicrogCompat.smali').read_bytes())
