import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect("157.180.43.233", username="root", password="FwRvmmAqsVHh")

def run(cmd, timeout=120):
    _, out, err = ssh.exec_command(cmd, timeout=timeout)
    o = out.read().decode().strip()
    e = err.read().decode().strip()
    if o: print(o[:1000])
    if e and "NOTICE" not in e: print("ERR:", e[:300])
    return o

# Upload and run retrain script inside ML container
retrain_py = """
import urllib.request, json
req = urllib.request.Request(
    'http://localhost:8000/retrain',
    data=b'{}',
    headers={'Content-Type': 'application/json'},
    method='POST'
)
try:
    resp = urllib.request.urlopen(req, timeout=60)
    body = resp.read().decode()
    print('Status:', resp.status)
    print('Response:', body[:500])
except Exception as e:
    print('Error:', e)
"""

sftp = ssh.open_sftp()
with sftp.open("/tmp/trigger_retrain.py", "w") as f:
    f.write(retrain_py)
sftp.close()

run("docker cp /tmp/trigger_retrain.py flowguard-ml:/tmp/trigger_retrain.py")
print(">>> Triggering ML retrain (POST /retrain)...")
run("docker exec flowguard-ml python3 /tmp/trigger_retrain.py", timeout=120)

ssh.close()
print("Done.")
