"""Create a read-only deployment snapshot without touching the personal trainer."""
import hashlib,json,shutil,sys
from pathlib import Path

HERE=Path(__file__).resolve().parent
REPO=HERE.parent
PERSONAL=REPO/'experiments/raha-parsbert-web'
sys.path.insert(0,str(PERSONAL))
from model_delta import decode

def main():
    target=HERE/'bundle'
    if target.exists():raise SystemExit('bundle already exists; archive it before preparing another snapshot')
    assets=REPO/'voyah-iran/raha-runtime/src/main/assets/raha-intent'
    manifest=json.loads((assets/'manifest.json').read_text())
    model=decode((assets/'model.base.onnx').read_bytes(),(assets/'model.delta').read_bytes())
    assert hashlib.sha256(model).hexdigest()==manifest['model_sha256']
    target.mkdir()
    (target/'model.onnx').write_bytes(model)
    shutil.copyfile(REPO/'voyah-iran/raha-core/src/test/resources/intent-tokenizer-parity.json',target/'parity-fixtures.json')
    for name in ['manifest.json','schema.json','vocab.txt','contact-rules.json']:
        shutil.copyfile(assets/name,target/name)
    for name in ['data.py','validation.py','numeric_grounding.py','contact_extraction.py','presentation.py']:
        shutil.copyfile(PERSONAL/name,target/name)
    grammar=(REPO/'voyah-iran/raha-core/src/main/resources/raha-speech-phrases.txt').read_text().splitlines()
    (target/'grammar.json').write_text(json.dumps(list(dict.fromkeys(grammar)),ensure_ascii=False))
    shutil.copytree(PERSONAL/'artifacts/vosk-model-small-fa-0.42',target/'vosk')
    for name in ['parameters.js','pcm.js']:
        shutil.copyfile(PERSONAL/'web'/name,target/name)
    editor=(PERSONAL/'web/feedback-editor.js').read_text()
    start=editor.index('export async function initializeEditor(){')
    end=editor.index('function renderFields(current){')
    editor=editor[:start]+'''export function setupEditor(titles,variants){
 schema=variants;actionOptions=Object.entries(titles).map(([value,title])=>({value,title}));
 $('action-search').oninput=filterActions;
 $('parameter-variant').onchange=()=>renderFields({});
 $('expected').onchange=()=>showEditor($('expected').value,{});
 filterActions();
}
'''+editor[end:]
    (target/'feedback-editor.js').write_text(editor)
    microphone=(PERSONAL/'web/microphone.js').read_text().replace("'/web/pcm.js?v=persistent-ack-2'","'./web/pcm.js'").replace('${location.host}/api/listen','${location.host}/raha-train/api/listen')
    microphone=microphone.replace('microphone({message,display})','microphone({message,display,listening=()=>{}})')
    microphone=microphone.replace("$('parse').disabled=false;",'').replace("$('parse').disabled=true;",'')
    microphone=microphone.replace('if(m.ready){','if(m.ready){listening();')
    (target/'microphone.js').write_text(microphone)
    files={str(p.relative_to(target)):hashlib.sha256(p.read_bytes()).hexdigest() for p in target.rglob('*') if p.is_file()}
    (target/'checksums.json').write_text(json.dumps(files,indent=2))
    print('Prepared immutable model snapshot:',manifest['model_sha256'])

if __name__=='__main__':main()
