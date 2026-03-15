import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('157.180.43.233', username='root', password='FwRvmmAqsVHh')

CLIENT_ID = 'sandbox_id_8854ed19940c493faeec55080d2bd98b'
CLIENT_SECRET = 'sandbox_secret_RImj9XJpPjl4vQIEqcV1vjfAWyUlZOrAa035DdezpU75HAxdUHMCORMLRipmO0wS'

print("=== Step 1: Get Bridge user UUID (list users) ===")
cmd = (
    f'curl -s '
    f'-X GET "https://api.bridgeapi.io/v3/aggregation/users?external_user_id=test-ip-check-001" '
    f'-H "client-id: {CLIENT_ID}" '
    f'-H "client-secret: {CLIENT_SECRET}" '
    f'-H "Bridge-Version: 2025-01-15"'
)
_, out, _ = ssh.exec_command(cmd)
users_resp = out.read().decode()
print('Users:', users_resp[:500])

import json
try:
    users_data = json.loads(users_resp)
    resources = users_data.get('resources', [])
    if resources:
        bridge_uuid = resources[0]['uuid']
        print('Bridge UUID:', bridge_uuid)
    else:
        print('No users found in resources')
        bridge_uuid = None
except Exception as e:
    print('Parse error:', e)
    bridge_uuid = None

if bridge_uuid:
    print()
    print("=== Step 2: Get token for user ===")
    cmd2 = (
        f'curl -s -w "\\nHTTP_STATUS:%{{http_code}}" '
        f'-X POST https://api.bridgeapi.io/v3/aggregation/authorization/token '
        f'-H "client-id: {CLIENT_ID}" '
        f'-H "client-secret: {CLIENT_SECRET}" '
        f'-H "Bridge-Version: 2025-01-15" '
        f'-H "Content-Type: application/json" '
        f'-d \'{{"user_uuid":"{bridge_uuid}"}}\''
    )
    _, out2, _ = ssh.exec_command(cmd2)
    token_resp = out2.read().decode()
    print('Token response:', token_resp[:600])

ssh.close()
