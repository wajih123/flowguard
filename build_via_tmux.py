"""
Launch Docker build inside tmux on the server so it survives SSH disconnects.
Uses build cache from previous attempt (faster).
"""
import paramiko
import time
import sys

HOST = "157.180.43.233"
USER = "root"
PASS = "Fl0wGu4rd_Serv3r#2026"
DEPLOY_DIR = "/opt/flowguard"
BUILD_LOG = "/tmp/fg-build.log"
TMUX_SESSION = "fgbuild"


def run(client, cmd, timeout=60):
    chan = client.get_transport().open_session()
    chan.set_combine_stderr(True)
    chan.exec_command(cmd)
    out = b""
    start = time.time()
    while True:
        if chan.recv_ready():
            out += chan.recv(65536)
        elif chan.exit_status_ready():
            while chan.recv_ready():
                out += chan.recv(65536)
            break
        elif time.time() - start > timeout:
            chan.close()
            return -1, out.decode("utf-8", errors="replace")
        else:
            time.sleep(0.3)
    return chan.recv_exit_status(), out.decode("utf-8", errors="replace")


client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(HOST, username=USER, password=PASS, timeout=15)
print("Connected!")

# Install tmux if missing
code, out = run(client, "which tmux || apt-get install -y -qq tmux 2>&1 | tail -2")
print(f"tmux: {out.strip()}")

# Kill any existing build session
run(client, f"tmux kill-session -t {TMUX_SESSION} 2>/dev/null; echo ok")

# Remove old log
run(client, f"rm -f {BUILD_LOG}")

# Start build in tmux background session
build_cmd = (
    f"cd {DEPLOY_DIR} && "
    f"docker compose -f infra/docker-compose.prod.yml "
    f"--env-file infra/.env.prod build --parallel "
    f"2>&1 | tee {BUILD_LOG}"
)
code, out = run(client, f"tmux new-session -d -s {TMUX_SESSION} '{build_cmd}'")
print(f"tmux start: {out.strip() or 'OK'} (exit {code})")

# Give it 3 seconds to start
time.sleep(3)

# Verify it started
code, out = run(client, f"tmux list-sessions 2>&1")
print(f"tmux sessions: {out.strip()}")

code, out = run(client, f"head -5 {BUILD_LOG} 2>/dev/null || echo '(log not yet)'")
print(f"Build log start: {out.strip()}")

print(f"\nBuild started in tmux session '{TMUX_SESSION}'")
print(f"Log file: {BUILD_LOG}")
print(f"To watch on server: tmux attach -t {TMUX_SESSION}")
print(f"\nNow running monitor loop (checking every 30s)...")
print("="*60)

# Monitor loop
for i in range(120):
    time.sleep(30)

    # Get new log tail
    code, log = run(client, f"tail -15 {BUILD_LOG} 2>/dev/null || echo '(no log)'", timeout=15)
    print(f"\n--- Check {i+1} (t={i*30+30}s) ---")
    print(log.strip())

    # Check if tmux session is still running
    code2, sessions = run(client, f"tmux list-sessions 2>&1")
    still_running = TMUX_SESSION in sessions

    # Check images
    code3, imgs = run(client, "docker image ls --format '{{.Repository}}:{{.Tag}}' 2>/dev/null")
    has_backend = "flowguard-backend" in imgs
    has_ml = "flowguard-ml" in imgs
    has_web = "flowguard-web" in imgs

    print(f"Build running: {still_running} | backend: {has_backend} | ml: {has_ml} | web: {has_web}")

    if has_backend and has_ml and has_web:
        print("\nAll 3 images built! Starting the stack...")
        break

    if not still_running and i > 1:
        print("\nBuild session ended. Checking for errors...")
        code, log_end = run(client, f"tail -60 {BUILD_LOG} 2>/dev/null")
        print(log_end)
        if not (has_backend and has_ml and has_web):
            print("Build seems to have failed.")
            client.close()
            sys.exit(1)
        break

# Start the stack
print("\nStarting Docker Compose stack...")
code, out = run(client,
    f"cd {DEPLOY_DIR} && "
    f"docker compose -f infra/docker-compose.prod.yml --env-file infra/.env.prod up -d 2>&1",
    timeout=120)
print(out)

if code != 0:
    print(f"Stack start exit={code}")
    client.close()
    sys.exit(1)

print("\nWaiting 30s for services to start...")
time.sleep(30)

# Status
code, out = run(client, f"docker compose -f {DEPLOY_DIR}/infra/docker-compose.prod.yml ps 2>&1", timeout=15)
print("\nContainers:")
print(out)

code, out = run(client,
    "curl -sf http://localhost/health-check 2>&1 || "
    "curl -sf http://localhost:8080/q/health 2>&1 || echo '(still starting)'",
    timeout=15)
print("\nHealth:")
print(out)

print(f"\n{'='*60}")
print(f"App: http://{HOST}")
print(f"Server password: {PASS}")
print('='*60)

client.close()
