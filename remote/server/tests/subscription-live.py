"""Run on the server: exercise a unique synthetic VIN, then remove only its rows."""
import base64,json,secrets,subprocess,urllib.request
vin='L'+''.join(str(secrets.randbelow(10)) for _ in range(16))
def sql(query):
 return subprocess.check_output(['docker','exec','voyah-iran-subscription-server-db-1','psql','-v','ON_ERROR_STOP=1','-U','voyahiran','-d','voyahiran','-Atc',query],text=True).strip()
def register():
 req=urllib.request.Request('http://127.0.0.1:8080/registerOrRenewSubscription',data=json.dumps({'vin':vin}).encode(),headers={'Content-Type':'application/json'})
 with urllib.request.urlopen(req,timeout=15) as r: response=json.load(r)
 part=response['token'].split('.')[1]
 return json.loads(base64.urlsafe_b64decode(part+'='*(-len(part)%4)))
assert sql(f"SELECT count(*) FROM subscriptions WHERE vin='{vin}'")=='0'
try:
 assert register()['remote_access'] is False
 sql(f"UPDATE subscriptions SET remote_access=TRUE,expires_at=NOW()-INTERVAL '1 day' WHERE vin='{vin}'")
 assert register()['remote_access'] is True
 assert sql(f"SELECT remote_access FROM subscriptions WHERE vin='{vin}'")=='t'
 sql(f"UPDATE subscriptions SET remote_access=FALSE WHERE vin='{vin}'")
 assert register()['remote_access'] is False
 print('PASS live API: default false, manual enable, renewal preservation, manual disable')
finally:
 sql(f"BEGIN; DELETE FROM renewal_events WHERE vin='{vin}'; DELETE FROM subscriptions WHERE vin='{vin}'; COMMIT;")
 print('Synthetic subscription removed')
