"""
FlowGuard — Deploy to Hetzner CAX11 via SSH (paramiko password auth)
Run: python deploy_to_server.py
"""
import sys
import time
import paramiko

HOST = "157.180.43.233"
USER = "root"
PASS_OLD = "W7hKFbLCUgaCqvjmuea3"
PASS_NEW = "Fl0wGu4rd_Serv3r#2026"  # new password, different from initial one
REPO = "https://github.com/wajih123/flowguard.git"
DEPLOY_DIR = "/opt/flowguard"

ENV_PROD = r"""POSTGRES_DB=flowguard
POSTGRES_USER=flowguard
POSTGRES_PASSWORD=Fl0wGu4rd_DB_Pr0d#2026
REDIS_PASSWORD=Fl0wGu4rd_R3d1s#2026
BRIDGE_CLIENT_ID=sandbox_id_8854ed19940c493faeec55080d2bd98b
BRIDGE_CLIENT_SECRET=sandbox_secret_RImj9XJpPjl4vQIEqcV1vjfAWyUlZOrAa035DdezpU75HAxdUHMCORMLRipmO0wS
BRIDGE_REDIRECT_URL=http://157.180.43.233/banking/callback
CORS_ORIGINS=http://157.180.43.233
SERVER_IP=157.180.43.233
DOMAIN=flowguard.fr
"""

# ---------------------------------------------------------------------------

def run(client: paramiko.SSHClient, cmd: str, timeout: int = 300) -> tuple[int, str]:
    """Run a command, stream output, return (exit_code, full_output)."""
    print(f"\n{'='*60}")
    print(f"$ {cmd[:120]}{'...' if len(cmd) > 120 else ''}")
    print('='*60)

    transport = client.get_transport()
    chan = transport.open_session()
    chan.set_combine_stderr(True)
    chan.exec_command(cmd)

    output_lines = []
    start = time.time()
    while True:
        if chan.recv_ready():
            chunk = chan.recv(4096).decode("utf-8", errors="replace")
            print(chunk, end="", flush=True)
            output_lines.append(chunk)
        elif chan.exit_status_ready():
            # Drain any remaining output
            while chan.recv_ready():
                chunk = chan.recv(4096).decode("utf-8", errors="replace")
                print(chunk, end="", flush=True)
                output_lines.append(chunk)
            break
        elif time.time() - start > timeout:
            print("\n[TIMEOUT]")
            chan.close()
            return -1, "".join(output_lines)
        else:
            time.sleep(0.2)

    exit_code = chan.recv_exit_status()
    status = "OK" if exit_code == 0 else f"FAILED (exit {exit_code})"
    print(f"\n[{status}]")
    return exit_code, "".join(output_lines)


def run_or_die(client, cmd, timeout=300):
    code, out = run(client, cmd, timeout)
    if code != 0:
        print(f"\n\nDEPLOYMENT ABORTED — command failed with exit {code}")
        sys.exit(1)
    return out


def main():
    print(f"\nConnecting to {USER}@{HOST} ...")
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())

    # Try new password first (in case we already changed it in a previous run)
    PASS = PASS_NEW
    try:
        client.connect(HOST, username=USER, password=PASS_NEW, timeout=20)
        print("Connected with new password!\n")
    except Exception:
        # Fall back to old password
        try:
            client.connect(HOST, username=USER, password=PASS_OLD, timeout=20)
            PASS = PASS_OLD
            print("Connected with old password!\n")
        except Exception as e:
            print(f"Connection failed: {e}")
            sys.exit(1)

    # ── 0. Handle expired password via interactive PTY ───────────────────────
    print("[CHECK] Testing if password change is required...")
    code, out = run(client, "echo SHELL_OK")
    if code != 0 or "SHELL_OK" not in out:
        print("[INFO] Password change required — handling via PTY...")
        shell = client.invoke_shell(term="xterm", width=200, height=50)
        shell.settimeout(30)
        buf = ""
        deadline = time.time() + 60
        # Read until we see a known prompt
        while time.time() < deadline:
            if shell.recv_ready():
                chunk = shell.recv(4096).decode("utf-8", errors="replace")
                print(chunk, end="", flush=True)
                buf += chunk
                low = buf.lower()
                if "current password:" in low:
                    time.sleep(0.5)
                    shell.send(PASS_OLD + "\n")   # send current (expired) password
                    buf = ""
                elif "new password:" in low:
                    time.sleep(0.5)
                    shell.send(PASS_NEW + "\n")   # set a new different password
                    buf = ""
                elif "retype new password:" in low or "re-enter" in low:
                    time.sleep(0.5)
                    shell.send(PASS_NEW + "\n")
                    buf = ""
                elif "password updated successfully" in low or "$ " in buf or "# " in buf:
                    print("\n[INFO] Password changed successfully!")
                    break
            else:
                time.sleep(0.2)
        shell.close()
        # Reconnect with the new password
        print("\n[INFO] Reconnecting with new password...")
        client.close()
        time.sleep(3)
        client = paramiko.SSHClient()
        client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        client.connect(HOST, username=USER, password=PASS_NEW, timeout=20)
        PASS = PASS_NEW
        print(f"Reconnected! New password is: {PASS_NEW}\n")

    # ── 1. Update packages and install Docker + git ──────────────────────────
    run_or_die(client,
        "apt-get update -qq && "
        "DEBIAN_FRONTEND=noninteractive apt-get install -y -qq "
        "ca-certificates curl gnupg git ufw lsb-release 2>&1 | tail -5",
        timeout=180)

    # Install Docker via official script if not already present
    run_or_die(client,
        "which docker > /dev/null 2>&1 || "
        "(curl -fsSL https://get.docker.com | sh 2>&1 | tail -20)",
        timeout=300)

    # Install docker compose plugin if missing
    run_or_die(client,
        "docker compose version > /dev/null 2>&1 || "
        "( mkdir -p /usr/local/lib/docker/cli-plugins && "
        "  curl -SL \"https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-aarch64\" "
        "  -o /usr/local/lib/docker/cli-plugins/docker-compose && "
        "  chmod +x /usr/local/lib/docker/cli-plugins/docker-compose )",
        timeout=120)

    # ── 2. Enable Docker ─────────────────────────────────────────────────────
    run_or_die(client, "systemctl enable --now docker")

    # ── 3. UFW firewall ───────────────────────────────────────────────────────
    run(client, "ufw --force reset")
    run_or_die(client, "ufw default deny incoming && ufw default allow outgoing")
    run_or_die(client, "ufw allow 22/tcp && ufw allow 80/tcp && ufw allow 443/tcp")
    run_or_die(client, "ufw --force enable")

    # ── 4. Clone / update repo ───────────────────────────────────────────────
    run_or_die(client,
        f"if [ -d {DEPLOY_DIR}/.git ]; then "
        f"  cd {DEPLOY_DIR} && git fetch --all && git reset --hard origin/main; "
        f"else "
        f"  git clone {REPO} {DEPLOY_DIR}; "
        f"fi",
        timeout=120)

    # ── 5. Write .env.prod ───────────────────────────────────────────────────
    # Use printf to avoid heredoc quoting issues with special chars
    env_escaped = ENV_PROD.replace("\\", "\\\\").replace("'", "'\\''")
    run_or_die(client,
        f"printf '%s' '{env_escaped}' > {DEPLOY_DIR}/infra/.env.prod && "
        f"chmod 600 {DEPLOY_DIR}/infra/.env.prod && "
        f"echo '.env.prod written OK'")

    # ── 6. Build the Docker images ───────────────────────────────────────────
    print("\n[BUILD] This will take several minutes (Maven + frontend build)...")
    run_or_die(client,
        f"cd {DEPLOY_DIR} && "
        "docker compose -f infra/docker-compose.prod.yml --env-file infra/.env.prod build --parallel 2>&1",
        timeout=1800)  # 30 min

    # ── 7. Start the stack ───────────────────────────────────────────────────
    run_or_die(client,
        f"cd {DEPLOY_DIR} && "
        "docker compose -f infra/docker-compose.prod.yml --env-file infra/.env.prod up -d 2>&1",
        timeout=120)

    # ── 8. Wait and verify ───────────────────────────────────────────────────
    print("\n[VERIFY] Waiting 20s for services to start...")
    time.sleep(20)
    run(client,
        f"cd {DEPLOY_DIR} && "
        "docker compose -f infra/docker-compose.prod.yml ps 2>&1")
    run(client, "curl -sf http://localhost/health-check 2>&1 || "
                "curl -sf http://localhost:8080/q/health 2>&1 || echo 'nginx may still be starting'")

    print(f"\n{'='*60}")
    print(f"DEPLOYMENT COMPLETE!")
    print(f"App: http://{HOST}")
    print(f"Health: http://{HOST}/q/health")
    print('='*60)

    client.close()


if __name__ == "__main__":
    main()
