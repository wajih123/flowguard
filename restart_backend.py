import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('157.180.43.233', username='root', password='FwRvmmAqsVHh')

print("[1] Restarting backend with updated env...")
_, out, _ = ssh.exec_command(
    'cd /opt/flowguard/infra && docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --force-recreate backend 2>&1'
)
print(out.read().decode())

import time
time.sleep(12)

print("[2] Backend status:")
_, out2, _ = ssh.exec_command('docker ps --filter name=flowguard-backend --format "{{.Names}} {{.Status}}"')
print(out2.read().decode())

print("[3] Checking logs for Redis...")
_, out3, _ = ssh.exec_command('docker logs flowguard-backend --tail 20 2>&1')
log = out3.read().decode()
print(log)

ssh.close()
