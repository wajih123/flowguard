# 🚨 FlowGuard Monitoring & Alerts Setup

## Critical Infrastructure Alerts

This monitoring system tracks:
1. **Public Server** (157.180.43.233) - API availability
2. **Docker Services** - Backend, ML Service, DB, Redis
3. **Infrastructure** - CPU, Disk, Memory
4. **API Performance** - Latency, error rates

---

## ⚙️ Required Configuration

Before alerts work, set these environment variables (in `docker-compose.yml` or `.env`):

### 1. **Slack Webhook (RECOMMENDED - Real-time notifications)**

```bash
# Get from: https://api.slack.com/apps/[YOUR_APP_ID]/incoming-webhooks
export SLACK_WEBHOOK_URL="https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXX"
```

### 2. **PagerDuty (For Oncall Response)**

```bash
# Get from: https://your-domain.pagerduty.com/services/PXXXXXX/integrations
export PAGERDUTY_SERVICE_KEY="XXXXXXXXXXXXXXXXXXXXXXXXXXXX"
```

### 3. **SendGrid Email (Optional - For email fallback)**

```bash
export SENDGRID_API_KEY="SG.XXXXXXXXXXXXXXXXXXXXXXXXXXXX"
```

---

## 📋 Alert Rules

### 🔴 CRITICAL (Immediate notification - 5 seconds wait)

- **PublicServerDown**: Server 157.180.43.233 unreachable (2min threshold)
- **BackendDown**: Internal backend service unavailable (1min threshold)
- **PostgresDown**: Database unreachable 
- **HighErrorRate**: >5% 5xx errors for > 3 minutes

### 🟡 WARNING (5min wait)

- **MLServiceDown**: ML service unavailable
- **RedisDown**: Cache/rate-limiting degraded
- **JvmMemoryHigh**: Heap usage > 90%
- **HostDiskSpaceLow**: Disk space < 10%

---

## 🔍 Monitoring Flow

```
Blackbox Exporter (9115)
  ↓ (HTTP probes)
Prometheus (9090)
  ↓ (rule evaluation)
Alert Rules (alerts.yml)
  ↓ (if triggered)
Alertmanager (9093)
  ↓ (routing)
├── Slack Webhook (RECOMMENDED)
├── PagerDuty API
├── Email (SendGrid)
└── Custom Webhooks
```

---

## 🚀 Deployment

### Local Development (Docker Compose)

```bash
# Start monitoring stack
docker-compose up -d prometheus alertmanager blackbox-exporter grafana

# Set webhook URLs
export SLACK_WEBHOOK_URL="..." PAGERDUTY_SERVICE_KEY="..."

# Restart alertmanager with env vars
docker-compose restart alertmanager
```

### Production (157.180.43.233)

```bash
ssh root@157.180.43.233

# Set environment variables
export SLACK_WEBHOOK_URL="..."
export PAGERDUTY_SERVICE_KEY="..."
export SENDGRID_API_KEY="..."

# Restart alertmanager
docker-compose -f docker-compose.prod.yml restart alertmanager
```

---

## 📊 Access Dashboards

- **Prometheus**: http://localhost:9090 (internal)
- **Grafana**: http://localhost:3000 (optional dashboard)
- **Alertmanager**: http://localhost:9093 (internal)

---

## ✅ Testing Alerts

```bash
# Trigger a test alert
docker-compose exec prometheus promtool alert test /etc/prometheus/alerts.yml

# View current alerts in Prometheus
# Go to: http://localhost:9090/alerts

# View firing alerts in Alertmanager
# Go to: http://localhost:9093
```

---

## 🐛 Troubleshooting

### Alerts not firing?

1. Check Prometheus scrape targets: http://localhost:9090/targets
2. Verify alert rules load: http://localhost:9090/rules
3. Check Alertmanager status: http://localhost:9093

### Public server appears down but it's actually up?

- Verify blackbox-exporter can reach it with: `curl -v https://157.180.43.233/api/health`
- May be firewall issue blocking Prometheus → public server connection
- Solution: Use external monitoring service (UptimeRobot, Datadog, etc)

### Webhooks not working?

1. Verify environment variables are set: `docker exec alertmanager env | grep WEBHOOK`
2. Check alertmanager logs: `docker logs alertmanager`
3. Test webhook URL manually: `curl -X POST $SLACK_WEBHOOK_URL -d '{"text":"test"}'`

---

## 🔄 Next Steps

1. **Configure monitoring notifications** using the setup instructions above
2. **Add external uptime monitoring** (e.g., UptimeRobot) for 157.180.43.233
3. **Set up Grafana dashboards** for visual monitoring
4. **Create runbooks** for each alert (what to do when it fires)
