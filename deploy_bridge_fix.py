import paramiko
import os

SERVER = '157.180.43.233'
USER = 'root'
PASS = 'FwRvmmAqsVHh'

LOCAL = r'c:\Users\User\flowguard\backend\src\main\java\com\flowguard\service\BridgeService.java'
REMOTE = '/opt/flowguard/backend/src/main/java/com/flowguard/service/BridgeService.java'

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(SERVER, username=USER, password=PASS)

print("[1] Uploading BridgeService.java...")
sftp = ssh.open_sftp()
sftp.put(LOCAL, REMOTE)
sftp.close()
print("    Done.")

print("[2] Building Docker image...")
stdin, out, err = ssh.exec_command(
    'cd /opt/flowguard/backend && docker build -t flowguard-backend:latest . 2>&1',
    timeout=300
)
for line in out:
    print(line, end='')
for line in err:
    print('ERR:', line, end='')

print("\n[3] Restarting backend...")
_, out2, _ = ssh.exec_command(
    'cd /opt/flowguard/infra && docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --force-recreate backend 2>&1'
)
print(out2.read().decode())

import time
time.sleep(12)

print("[4] Backend logs:")
_, out3, _ = ssh.exec_command('docker logs flowguard-backend --tail 8 2>&1')
print(out3.read().decode())

ssh.close()
print("=== Done ===")
