"""Keep one ORT native runtime: Sherpa's newer runtime serves the stable C API.

The official Java 1.19.2 JNI uses OrtGetApiBase()->GetApi(19). The existing
Sherpa AAR supplies ORT 1.28.2. Do not use Gradle pickFirst (order-dependent).
This repack keeps the official Java classes, JNI bridge, license and metadata,
and removes ONLY its older duplicate libonnxruntime.so. Never strips Sherpa.
Android device validation remains required before release.
"""
from pathlib import Path
import hashlib,sys,zipfile

def main():
    source=Path(sys.argv[1]);target=Path(sys.argv[2])
    target.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(source) as src,zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as dst:
        for item in src.infolist():
            if item.filename.startswith('jni/') and item.filename.endswith('/libonnxruntime.so'):continue
            dst.writestr(item,src.read(item.filename))
    print('Upstream SHA256:',hashlib.sha256(source.read_bytes()).hexdigest())

if __name__=='__main__':main()
