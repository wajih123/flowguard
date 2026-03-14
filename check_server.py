"""Quick server status check"""
import paramiko

HOST = "157.180.43.233"
USER = "root"
PASS = "Fl0wGu4rd_Serv3r#2026"

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(HOST, username=USER, password=PASS, timeout=10)

checks = [
    ("DOCKER IMAGES", "docker image ls"),
    ("RUNNING CONTAINERS", "docker ps"),
    ("BUILD PROCESSES", "ps aux | grep -i docker | grep -v grep | wc -l"),
    ("BUILDX STATUS", "docker buildx ls 2>&1 | head -10"),
    ("DISK", "df -h / | tail -1"),
    ("MEMORY", "free -h | head -2"),
    ("RECENT DOCKER EVENTS", "docker events --since 5m --until 0s --format '{{.Time}} {{.Type}} {{.Action}} {{.Actor.Attributes.name}}' 2>/dev/null | tail -10 || true"),
]

for label, cmd in checks:
    _, out, _ = c.exec_command(cmd)
    result = out.read().decode().strip()
    print(f"\n{'='*50}")
    print(f"[{label}]")
    print(result or "(empty)")

c.close()

