"""Export the production vehicle decoder vocabulary, not intent-training data."""
import hashlib
import json
import subprocess
import os
from pathlib import Path
from prepare_full import ROOT, CORE, JAVA

def main():
    jar = CORE / 'build/libs/command-core-0.1.0.jar'
    libs = [p for p in (Path.home()/'.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib').glob('*/**/kotlin-stdlib-*.jar')
            if not p.name.endswith(('-sources.jar','-javadoc.jar'))]
    if not libs or not jar.exists():
        raise RuntimeError('Build :command-core:jar before exporting vocabulary')
    phrases = subprocess.check_output([JAVA+'java','-Dfile.encoding=UTF-8','--class-path',
        os.pathsep.join(map(str,[jar,sorted(libs)[-1]])),str(ROOT/'SpeechVocabularyExport.java')], encoding='utf-8').splitlines()
    if not phrases or '[unk]' not in phrases:
        raise RuntimeError('Production grammar export is incomplete')
    jar = CORE / 'build/libs/command-core-0.1.0.jar'
    document = {'source': 'RahaSpeechVocabulary.phrases()',
                'core_jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
                'phrases': phrases}
    (ROOT / 'speech-grammar.json').write_text(json.dumps(document, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'Exported {len(phrases)} production vehicle phrases')

if __name__ == '__main__':
    main()
