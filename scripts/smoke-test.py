"""Run against real MySQL-backed backend. Requires stdlib Python and mysql CLI for cleanup.
Creates only a uniquely named temporary user, then removes that user in finally.
"""
import base64, hashlib, hmac, json, os, pathlib, secrets, subprocess, time, urllib.request, urllib.error
ROOT=pathlib.Path(__file__).resolve().parents[1]
BASE=os.getenv('MEDIAFLOW_API','http://127.0.0.1:8080')
config={}
for line in (ROOT/'.env').read_text(encoding='utf-8-sig').splitlines():
 if '=' in line and not line.startswith('#'):
  k,v=line.split('=',1);config[k]=v
checks=[]
def request(method,path,expected=200,data=None,token=None):
 headers={'Content-Type':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 req=urllib.request.Request(BASE+path,data=None if data is None else json.dumps(data).encode(),headers=headers,method=method)
 try:
  with urllib.request.urlopen(req,timeout=20) as r:status=r.status;payload=json.load(r)
 except urllib.error.HTTPError as e:status=e.code;payload=json.load(e)
 assert status==expected,f'{method} {path}: expected {expected}, got {status}'
 assert payload['code']==(0 if expected<400 else expected*100),f'Wrong business code: {payload["code"]}'
 checks.append({'method':method,'path':path,'expected':expected,'actual':status})
 return payload['data']
def login(user,password):return request('POST','/api/auth/login',data={'username':user,'password':password})['accessToken']
uid=None
try:
 admin=login('admin','Admin@123456')
 editor=login('editor01','Editor@123456')
 reviewer=login('reviewer01','Reviewer@123456')
 request('POST','/api/auth/login',401,{'username':'admin','password':'Wrong@123456'})
 request('GET','/api/auth/me',401)
 request('GET','/api/admin/users',403,token=editor)
 request('GET','/api/admin/users',403,token=reviewer)
 request('GET','/api/admin/users',token=admin)
 request('GET','/api/admin/roles',token=admin)
 request('GET','/api/auth/me',401,token='illegal.jwt.value')
 def b64(value):return base64.urlsafe_b64encode(json.dumps(value,separators=(',',':')).encode()).decode().rstrip('=')
 now=int(time.time());unsigned=b64({'alg':'HS256','typ':'JWT'})+'.'+b64({'iss':'mediaflow','sub':'1','iat':now-60,'exp':now-1})
 sig=base64.urlsafe_b64encode(hmac.new(config['JWT_SECRET'].encode(),unsigned.encode(),hashlib.sha256).digest()).decode().rstrip('=')
 request('GET','/api/auth/me',401,token=unsigned+'.'+sig)
 name='accept_'+secrets.token_hex(5)
 create={'username':name,'displayName':'验收临时用户','password':'Accept@Test123456','roles':['EDITOR','REVIEWER']}
 user=request('POST','/api/admin/users',201,create,admin);uid=user['id'];assert len(user['roles'])==2
 request('POST','/api/admin/users',409,create,admin)
 t=login(name,create['password'])
 request('GET','/api/admin/users',403,token=t)
 request('PUT',f'/api/admin/users/{uid}/roles',data={'roles':['ADMIN','EDITOR']},token=admin)
 request('GET','/api/admin/users',token=t)
 request('PUT',f'/api/admin/users/{uid}/roles',data={'roles':['EDITOR']},token=admin)
 request('GET','/api/admin/users',403,token=t)
 request('PUT',f'/api/admin/users/{uid}',data={'displayName':'验收已禁用','status':0},token=admin)
 request('GET','/api/auth/me',401,token=t)
 request('POST','/api/auth/login',401,{'username':name,'password':create['password']})
 request('GET','/api/admin/users/99999999',404,token=admin)
 request('GET','/api/admin/users?page=0',400,token=admin)
 request('POST','/api/auth/login',400,{})
 print(f'PASS: {len(checks)} real HTTP/MySQL checks')
 (ROOT/'.local').mkdir(exist_ok=True)
 (ROOT/'.local'/'smoke-results.json').write_text(json.dumps({'passed':len(checks),'checks':checks},ensure_ascii=False,indent=2),encoding='utf-8')
finally:
 if uid is not None:
  env=os.environ.copy();env['MYSQL_PWD']=config['DB_PASSWORD']
  subprocess.run(['mysql','-h','localhost','-u',config.get('DB_USERNAME','root'),'mediaflow','-e',f"DELETE FROM sys_user WHERE id={int(uid)} AND username='{name}';"],env=env,check=True)
