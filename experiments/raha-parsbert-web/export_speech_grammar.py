"""Export the production vehicle decoder vocabulary, not intent-training data."""
import hashlib
import json
import subprocess
from prepare_full import ROOT, CORE, teacher_setup

def main():
    phrases = subprocess.check_output(teacher_setup() + ['vocabulary'], encoding='utf-8').splitlines()
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
