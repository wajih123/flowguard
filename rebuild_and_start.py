"""
Re-run Docker build + start stack on server.
Uses nohup so build survives SSH disconnects.
Monitors log until done.
"""
import paramiko
import time
import sys

HOST = "157.180.43.233"
USER = "root"
PASS = "Fl0wGu4rd_Serv3r#2026"
DEPLOY_DIR = "/opt/flowguard"
BUILD_LOG = "/tmp/flowguard-build.log"


def ssh_run(client, cmd, timeout=30):
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


def connect():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username=USER, password=PASS, timeout=15)
    return c


def main():
    print(f"Connecting to {HOST}...")
    client = connect()
    print("Connected!\n")

    # Check if build is already running in background
    code, out = ssh_run(client, "pgrep -f 'docker compose' 2>/dev/null | head -3")
    if code == 0 and out.strip():
        print(f"Build already running (PIDs: {out.strip()})")
    else:
        # Launch build in background with nohup so it survives SSH disconnect
        print("Launching Docker build in background (nohup)...")
        cmd = (
            f"cd {DEPLOY_DIR} && "
            f"nohup docker compose -f infra/docker-compose.prod.yml "
            f"--env-file infra/.env.prod build --parallel "
            f"> {BUILD_LOG} 2>&1 &"
            f"echo $!"
        )
        code, pid_out = ssh_run(client, cmd, timeout=30)
        print(f"Build PID: {pid_out.strip()}")

    # Monitor the build log
    print(f"\nMonitoring {BUILD_LOG} (Ctrl+C to stop monitoring, build continues on server)...")
    print("="*60)

    last_pos = 0
    checks = 0
    max_checks = 120  # 60 minutes max

    while checks < max_checks:
        time.sleep(30)
        checks += 1

        # Get new log output
        code, log = ssh_run(client,
            f"tail -c +{last_pos + 1} {BUILD_LOG} 2>/dev/null || echo '(no log yet)'",
            timeout=15)
        if log.strip() and log.strip() != "(no log yet)":
            print(log, end="", flush=True)
            last_pos += len(log.encode("utf-8"))

        # Check if build process is still running
        code2, procs = ssh_run(client, "pgrep -f 'docker compose' 2>/dev/null | wc -l")
        build_running = code2 == 0 and procs.strip() not in ("0", "")

        # Check if images were built
        code3, imgs = ssh_run(client, "docker image ls --format '{{.Repository}}' 2>/dev/null")
        has_images = any(name in imgs for name in ["flowguard-backend", "flowguard-ml", "flowguard-web"])

        print(f"\n[Check {checks}] Build running: {build_running} | Images built: {has_images}")

        if has_images:
            print("\n✓ Build completed! Starting the stack...")
            break

        if not build_running and checks > 2:
            print("\nBuild process ended. Checking result...")
            # Print last 50 lines of log
            code, log_end = ssh_run(client, f"tail -50 {BUILD_LOG} 2>/dev/null")
            print("Last build log:\n" + log_end)
            # Check for errors
            if "ERROR" in log_end.upper() or "FAILED" in log_end.upper():
                print("\nBuild FAILED. See log above.")
                client.close()
                sys.exit(1)
            else:
                print("\nBuild seems complete (no error found). Trying to start stack...")
                break

    # Start the stack
    print("\nStarting Docker Compose stack...")
    code, out = ssh_run(client,
        f"cd {DEPLOY_DIR} && "
        f"docker compose -f infra/docker-compose.prod.yml --env-file infra/.env.prod up -d 2>&1",
        timeout=120)
    print(out)

    if code != 0:
        print(f"Stack start failed (exit {code})")
        client.close()
        sys.exit(1)

    # Wait for startup
    print("\nWaiting 30s for services to start...")
    time.sleep(30)

    # Check running containers
    code, out = ssh_run(client,
        f"docker compose -f {DEPLOY_DIR}/infra/docker-compose.prod.yml ps 2>&1", timeout=15)
    print("\nRunning containers:")
    print(out)

    # Quick health check
    code, out = ssh_run(client,
        "curl -sf http://localhost/health-check 2>&1 || "
        "curl -sf http://localhost:8080/q/health 2>&1 || echo '(nginx starting...)'",
        timeout=15)
    print("\nHealth check:")
    print(out)

    print(f"\n{'='*60}")
    print(f"DONE! App should be available at: http://{HOST}")
    print(f"New server password: {PASS}")
    print('='*60)

    client.close()


if __name__ == "__main__":
    main()
