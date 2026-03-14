"""Check build status in detail"""
import paramiko

HOST = "157.180.43.233"
USER = "root"
PASS = "Fl0wGu4rd_Serv3r#2026"

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(HOST, username=USER, password=PASS, timeout=10)

cmds = [
    ("BUILDX CACHE SIZE", "docker system df 2>&1"),
    ("BUILDX RUNNING BUILDS", "docker buildx ls --format json 2>&1 | head -20"),
    ("DOCKER PS ALL", "docker ps -a 2>&1"),
    ("AVAILABLE IMAGES", "docker image ls -a 2>&1"),
    ("RUNNING PROCESSES", "ps aux --sort=-%cpu | head -15"),
    ("BUILD LOG (last 30 lines)",
     "journalctl -u docker --since '10 minutes ago' --no-pager -n 30 2>&1 | tail -30"),
]

for label, cmd in cmds:
    _, out, _ = c.exec_command(cmd)
    result = out.read().decode().strip()
    print(f"\n{'='*55}")
    print(f"[{label}]")
    print(result or "(empty)")

c.close()
