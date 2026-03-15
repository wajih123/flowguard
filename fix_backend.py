import paramiko, time

HOST = '157.180.43.233'
USER = 'root'
PASS = 'FwRvmmAqsVHh'

def ssh_exec(cmd, timeout=30):
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PASS, timeout=10)
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode('utf-8', errors='replace')
    err = stderr.read().decode('utf-8', errors='replace')
    rc = stdout.channel.recv_exit_status()
    ssh.close()
    return out, err, rc

import time

# Fix 1: Remove default.conf from nginx container and reload
print('[FIX 1] Removing nginx default.conf and reloading...')
out, err, rc = ssh_exec(
    "docker exec flowguard-nginx sh -c "
    "'rm -f /etc/nginx/conf.d/default.conf && nginx -s reload && echo done'"
)
print(out or err)

# Fix 2: Update compose so default.conf stays removed after restart
print('[FIX 2] Updating compose nginx command...')
cmd = r"""cd /opt/flowguard/infra && \
grep -q 'rm.*default.conf' docker-compose.prod.yml && echo 'already patched' || \
sed -i '/container_name: flowguard-nginx/a\    command: >-\n      sh -c "rm -f /etc/nginx/conf.d/default.conf \&\& nginx -g '"'"'daemon off;'"'"'"' docker-compose.prod.yml && \
echo 'patched'"""
out, err, rc = ssh_exec(cmd)
print(out or err)

# Fix 3: Update bcrypt hashes from $2b$ to $2a$ in DB
print('[FIX 3] Fixing bcrypt hash prefix $2b$ → $2a$...')
out, err, rc = ssh_exec(
    "docker exec flowguard-db psql -U flowguard -d flowguard -c "
    "\"UPDATE users SET password_hash = replace(password_hash, '\\$2b\\$', '\\$2a\\$') "
    "WHERE password_hash LIKE '\\$2b\\$%'; SELECT email, LEFT(password_hash,7) FROM users;\""
)
print(out or err)

# Wait for nginx reload
time.sleep(2)

# Test login via nginx
print('[TEST] Login via nginx...')
out, err, rc = ssh_exec(
    "curl -s -w '\\nHTTP:%{http_code}' --max-time 10 "
    "-X POST http://localhost/api/auth/login "
    "-H 'Content-Type: application/json' "
    "-d '{\"email\":\"admin@dev.fr\",\"password\":\"Test1234!\"}'",
    timeout=15
)
print(out or err)
















