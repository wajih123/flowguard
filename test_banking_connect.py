import urllib.request
import json
import ssl

ctx = ssl.create_default_context()
base = 'https://157-180-43-233.sslip.io'

req = urllib.request.Request(
    f'{base}/api/auth/login',
    data=json.dumps({'email': 'alice@dev.fr', 'password': 'Test1234!'}).encode(),
    headers={'Content-Type': 'application/json'}
)
with urllib.request.urlopen(req, context=ctx) as r:
    token = json.loads(r.read())['accessToken']
print('Login OK')

req2 = urllib.request.Request(
    f'{base}/api/banking/connect/start',
    data=b'{}',
    headers={'Content-Type': 'application/json', 'Authorization': f'Bearer {token}'}
)
try:
    with urllib.request.urlopen(req2, context=ctx) as r:
        resp = json.loads(r.read())
    print('Banking connect OK:')
    print(json.dumps(resp, indent=2))
except urllib.error.HTTPError as e:
    body = e.read().decode()
    print(f'Banking connect {e.code}: {body[:800]}')
