import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('157.180.43.233', username='root', password='FwRvmmAqsVHh')

CLIENT_ID = 'sandbox_id_8854ed19940c493faeec55080d2bd98b'
CLIENT_SECRET = 'sandbox_secret_RImj9XJpPjl4vQIEqcV1vjfAWyUlZOrAa035DdezpU75HAxdUHMCORMLRipmO0wS'
EXTERNAL_USER_ID = 'test-ip-check-001'

print("=== Test 1: Create Bridge user (app-level, needs IP whitelist) ===")
cmd = (
    f'curl -s -o /dev/null -w "%{{http_code}}" '
    f'-X POST https://api.bridgeapi.io/v3/aggregation/users '
    f'-H "client-id: {CLIENT_ID}" '
    f'-H "client-secret: {CLIENT_SECRET}" '
    f'-H "Bridge-Version: 2025-01-15" '
    f'-H "Content-Type: application/json" '
    f'-d \'{{"external_user_id":"{EXTERNAL_USER_ID}"}}\''
)
_, out, _ = ssh.exec_command(cmd)
print('HTTP status:', out.read().decode())

print()
print("=== Test 2: Full response for create user ===")
cmd2 = (
    f'curl -s '
    f'-X POST https://api.bridgeapi.io/v3/aggregation/users '
    f'-H "client-id: {CLIENT_ID}" '
    f'-H "client-secret: {CLIENT_SECRET}" '
    f'-H "Bridge-Version: 2025-01-15" '
    f'-H "Content-Type: application/json" '
    f'-d \'{{"external_user_id":"{EXTERNAL_USER_ID}"}}\''
)
_, out2, _ = ssh.exec_command(cmd2)
print('Response:', out2.read().decode()[:500])

ssh.close()
