"""Explicit local-only approval; never run on the public server."""
import argparse,hashlib,json,urllib.request

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('file');parser.add_argument('--ids',nargs='+',required=True)
    parser.add_argument('--apply-reviewed-sha256',help='Exact SHA256 printed by dry run, after human review')
    args=parser.parse_args()
    with open(args.file,'rb') as f:raw=f.read()
    digest=hashlib.sha256(raw).hexdigest();rows=[json.loads(line) for line in raw.decode().splitlines() if line.strip()]
    selected={r['id']:r for r in rows if r.get('id') in args.ids}
    if len(selected)!=len(set(args.ids)):raise SystemExit('Some requested record IDs were not found')
    for row in selected.values():
        print(json.dumps({k:row[k] for k in ['id','text','action','parameters','tester','model']},ensure_ascii=False))
    print('Reviewed file SHA256:',digest)
    if not args.apply_reviewed_sha256:
        print('Dry run only. Review the records, then provide --apply-reviewed-sha256.');return
    if args.apply_reviewed_sha256!=digest:raise SystemExit('File changed since review; refusing import')
    for row in selected.values():
        payload={k:row[k] for k in ['text','action','parameters']}
        request=urllib.request.Request('http://127.0.0.1:18766/api/feedback',json.dumps(payload).encode(),{'Content-Type':'application/json'})
        with urllib.request.urlopen(request,timeout=180) as response:result=json.load(response)
        print('Imported',row['id'],json.dumps(result.get('result'),ensure_ascii=False))

if __name__=='__main__':main()
