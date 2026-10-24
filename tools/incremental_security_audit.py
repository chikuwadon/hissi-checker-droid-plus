#!/usr/bin/env python3
"""Repository pre-publication audit. Emits categories and locations, never secret values."""
import base64,io,json,os,re,subprocess,tarfile,urllib.request,urllib.error,urllib.parse,zipfile
from collections import Counter
API='https://api.github.com/repos/'+os.environ['GITHUB_REPOSITORY']
TOKEN=os.environ['GITHUB_TOKEN']
MAX=15*1024*1024
RESULT={'git':{},'actions':{},'findings':[],'unverified':[]}
P={
'private_key':rb'-----BEGIN (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----',
'github_token':rb'\b(?:gh[pousr]_[A-Za-z0-9_]{18,}|github_pat_[A-Za-z0-9_]{30,})\b',
'aws_access_key':rb'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b',
'google_api_key':rb'\bAIza[A-Za-z0-9_-]{30,}\b',
'slack_token':rb'\bxox[baprs]-[A-Za-z0-9-]{16,}\b',
'credential_literal':rb'(?i)(?:password|passwd|api[_-]?key|private[_-]?key|client[_-]?secret|auth[_-]?token|access[_-]?token)\s*[:=]\s*["\x27](?!\$|\$\{|\*\*\*|REDACTED|<)[^"\x27\r\n]{8,}["\x27]',
'bearer_header':rb'(?i)Authorization\s*[:=]\s*Bearer\s+(?!\*\*\*|\$)[A-Za-z0-9_\.\-/+=]{24,}',
'cookie_header':rb'(?i)(?:Cookie|Set-Cookie)\s*:\s*(?!\*\*\*|<redacted>)[^\r\n]{35,}',
'keystore_base64':rb'(?i)(?:HISSI_KEYSTORE_B64|KEY_B64)\s*[:=]\s*["\x27]?[A-Za-z0-9+/]{80,}={0,2}',
}
P={k:re.compile(v) for k,v in P.items()}
SENSITIVE=re.compile(r'(?i)(?:^|/)(?:\.env(?:\..*)?|id_(?:rsa|ed25519)|[^/]+\.(?:jks|p12|pfx|keystore|pem|key)|google-services\.json|credentials(?:\.\w+)?\.json)$')
def flag(bucket,loc,kind):
    RESULT[bucket].append({'location':loc[:210],'category':kind})
def inspect(data,loc,depth=0):
    if len(data)>MAX:flag('unverified',loc,'oversize');return
    for k,p in P.items():
        if p.search(data):flag('findings',loc,k)
    if depth>=3:return
    if loc.lower().endswith(('.b64','.base64')):
        try:inspect(base64.b64decode(re.sub(rb'\s+',b'',data),validate=True),loc+':decoded',depth+1)
        except Exception:flag('unverified',loc,'base64_decode_failed')
        return
    if zipfile.is_zipfile(io.BytesIO(data)):
        try:
            with zipfile.ZipFile(io.BytesIO(data)) as z:
                members=z.infolist()
                if len(members)>1000:flag('unverified',loc,'too_many_zip_entries')
                for m in members[:1000]:
                    if m.is_dir():continue
                    name=loc+':'+m.filename
                    if SENSITIVE.search(m.filename):flag('findings',name,'sensitive_filename')
                    if m.file_size>MAX:flag('unverified',name,'large_zip_entry');continue
                    if m.flag_bits&1:flag('unverified',name,'encrypted_zip_entry');continue
                    try:inspect(z.read(m),name,depth+1)
                    except Exception:flag('unverified',name,'unreadable_zip_entry')
        except Exception:flag('unverified',loc,'zip_decode_failed')
    elif data.startswith((b'\xfd7zXZ\x00',b'\x1f\x8b')) or loc.endswith(('.tar','.tar.gz','.tar.xz')):
        try:
            with tarfile.open(fileobj=io.BytesIO(data),mode='r:*') as t:
                members=t.getmembers()
                if len(members)>1000:flag('unverified',loc,'too_many_tar_entries')
                for m in members[:1000]:
                    if not m.isfile():continue
                    name=loc+':'+m.name
                    if SENSITIVE.search(m.name):flag('findings',name,'sensitive_filename')
                    if m.size>MAX:flag('unverified',name,'large_tar_entry');continue
                    f=t.extractfile(m)
                    if f:inspect(f.read(MAX+1),name,depth+1)
        except Exception:flag('unverified',loc,'tar_decode_failed')
def git(*args):
    x=subprocess.run(['git',*args],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL)
    return x.stdout if x.returncode==0 else None
class Redirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,req,fp,code,msg,headers,newurl):
        out=super().redirect_request(req,fp,code,msg,headers,newurl)
        if out and urllib.parse.urlsplit(req.full_url).hostname!=urllib.parse.urlsplit(newurl).hostname:
            out.remove_header('Authorization')
        return out
opener=urllib.request.build_opener(Redirect)
def request(path,allow_missing=False,cap=MAX):
    req=urllib.request.Request(API+path,headers={'Authorization':'Bearer '+TOKEN,'Accept':'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28'})
    try:
        with opener.open(req,timeout=50) as r:
            b=r.read(cap+1)
            if len(b)>cap:raise ValueError('over size limit')
            return b
    except urllib.error.HTTPError as ex:
        if allow_missing and ex.code in (403,404,410):return None
        raise
def json_api(path):return json.loads(request(path,cap=6*1024*1024))
def list_runs():
    runs=[]
    for page in range(1,15):
        chunk=json_api('/actions/runs?per_page=100&page='+str(page))['workflow_runs']
        runs+=chunk
        if len(chunk)<100:break
    return runs
def history(runs):
    baseline=set((git('rev-list','--all') or b'').decode().splitlines())
    old=sorted({r['head_sha'] for r in runs if r.get('head_sha') and r['head_sha'] not in baseline})
    recovered,missing=[],[]
    for sha in old:
        if git('fetch','-q','--no-tags','origin',sha+':refs/audit/'+sha) is not None:recovered.append(sha[:12])
        else:missing.append(sha[:12])
    objects=(git('rev-list','--objects','--all') or b'').splitlines()
    scanned=0;seen=set()
    for row in objects:
        parts=row.split(b' ',1);sha=parts[0].decode()
        if sha in seen:continue
        seen.add(sha)
        typ=git('cat-file','-t',sha)
        if typ is None or typ.strip()!=b'blob':continue
        path=parts[1].decode('utf-8','replace') if len(parts)>1 else '(unnamed)'
        loc='git:'+path
        if SENSITIVE.search(path):flag('findings',loc,'sensitive_filename')
        size=git('cat-file','-s',sha)
        if size is None:flag('unverified',loc,'unreadable_blob');continue
        if int(size)>MAX:flag('unverified',loc,'oversize_blob');continue
        blob=git('cat-file','blob',sha)
        if blob is not None:inspect(blob,loc);scanned+=1
    RESULT['git']={'blobs_scanned':scanned,'objects_enumerated':len(seen),'old_run_heads':len(old),'recovered':len(recovered),'unavailable_shas':missing}
def actions(runs):
    count=Counter();missing=[]
    for idx,r in enumerate(runs,1):
        runid=r['id']
        try:jobs=json_api('/actions/runs/'+str(runid)+'/jobs?per_page=100')['jobs']
        except Exception:missing.append({'run':runid,'what':'jobs'});continue
        for job in jobs:
            count['jobs_listed']+=1
            try:
                data=request('/actions/jobs/'+str(job['id'])+'/logs',allow_missing=True,cap=12*1024*1024)
                if data is None:missing.append({'run':runid,'what':'job_log'});continue
                inspect(data,'actions:'+str(runid)+':job:'+str(job['id']))
                count['job_logs_scanned']+=1
            except Exception:missing.append({'run':runid,'what':'job_log'})
        try:arts=json_api('/actions/runs/'+str(runid)+'/artifacts?per_page=100')['artifacts']
        except Exception:missing.append({'run':runid,'what':'artifact_listing'});continue
        for a in arts:
            count['artifacts_listed']+=1
            if a.get('expired'):count['expired_artifacts']+=1;continue
            try:
                data=request('/actions/artifacts/'+str(a['id'])+'/zip',allow_missing=True)
                if data is None:missing.append({'run':runid,'what':'artifact_zip'});continue
                inspect(data,'actions:'+str(runid)+':artifact:'+str(a['id'])+':'+a.get('name','')[:50])
                count['artifacts_scanned']+=1
            except Exception:missing.append({'run':runid,'what':'artifact_zip'})
        if idx%20==0:print('AUDIT_PROGRESS '+str(idx)+'/'+str(len(runs)),flush=True)
    RESULT['actions']={'runs':len(runs),**dict(count),'unavailable':missing}
runs=[r for r in list_runs()
      if r['id']>=40002421099 and r['id']!=int(os.environ.get('GITHUB_RUN_ID','0'))]
print('INCREMENTAL_AUDIT_RUNS '+str(len(runs)),flush=True)
history(runs)
actions(runs)
with open('security-audit-report.json','w',encoding='utf-8') as f:json.dump(RESULT,f,ensure_ascii=False,indent=2)
print('AUDIT_SUMMARY '+json.dumps({'git':RESULT['git'],'actions':{k:v for k,v in RESULT['actions'].items() if k!='unavailable'},'findings_count':len(RESULT['findings']),'unverified_count':len(RESULT['unverified']),'unavailable_count':len(RESULT['actions']['unavailable'])}))
