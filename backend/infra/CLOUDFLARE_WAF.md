# FlowGuard — Cloudflare WAF Configuration

# DDoS protection, bot management, rate limiting

# Last updated: 2025-07-01

#

# Setup: Cloudflare dashboard → [your zone] → Security

# Terraform config below is provided as reference — apply manually or via cf-terraform.

---

## 1. DNS Configuration (Cloudflare Proxy enabled)

All traffic must flow through Cloudflare's proxy (orange cloud icon in DNS settings).

| Record             | Type  | Value               | Proxy      |
| ------------------ | ----- | ------------------- | ---------- |
| `@` (flowguard.fr) | A     | [Hetzner server IP] | ✅ Proxied |
| `www`              | CNAME | `flowguard.fr`      | ✅ Proxied |
| `api`              | A     | [Hetzner server IP] | ✅ Proxied |

**IMPORTANT:** The origin server IP must be unknown to attackers.
Configure Hetzner firewall to only accept inbound traffic from Cloudflare IP ranges:
https://www.cloudflare.com/ips-v4

---

## 2. SSL/TLS Settings

- **Mode**: Full (strict)
- **Min TLS version**: TLS 1.2
- **TLS 1.3**: Enabled
- **HSTS**: max-age=31536000; includeSubDomains; preload (in HTTP Response Headers)

---

## 3. Security Level

- **Security Level**: High
- **Bot Fight Mode**: On
- **Challenge Passage**: 30 minutes

---

## 4. Rate Limiting Rules

### 4.1 — Login Rate Limit (Brute Force Protection)

| Field          | Value                                              |
| -------------- | -------------------------------------------------- |
| Rule name      | `flowguard-auth-rate-limit`                        |
| Filter         | `http.request.uri.path contains "/api/auth/login"` |
| Rate: requests | 10 per 1 minute per IP                             |
| Action         | Block (429)                                        |
| Duration       | 10 minutes                                         |

### 4.2 — Registration Rate Limit

| Field     | Value                                                 |
| --------- | ----------------------------------------------------- |
| Rule name | `flowguard-register-rate-limit`                       |
| Filter    | `http.request.uri.path contains "/api/auth/register"` |
| Rate      | 5 per 10 minutes per IP                               |
| Action    | Block (429)                                           |
| Duration  | 1 hour                                                |

### 4.3 — API Global Rate Limit

| Field     | Value                                       |
| --------- | ------------------------------------------- |
| Rule name | `flowguard-api-global-rate-limit`           |
| Filter    | `http.request.uri.path starts_with "/api/"` |
| Rate      | 200 per 1 minute per IP                     |
| Action    | Block (429)                                 |
| Duration  | 5 minutes                                   |

### 4.4 — Flash Credit Rate Limit

| Field     | Value                                                                                  |
| --------- | -------------------------------------------------------------------------------------- |
| Rule name | `flowguard-flash-credit-rate-limit`                                                    |
| Filter    | `http.request.uri.path contains "/api/flash-credit" and http.request.method eq "POST"` |
| Rate      | 3 per 1 hour per IP                                                                    |
| Action    | Block (429)                                                                            |
| Duration  | 24 hours                                                                               |

---

## 5. WAF Custom Rules (Firewall Rules)

### 5.1 — Block Admin Paths from Non-Allowlisted IPs

```
(http.request.uri.path starts_with "/api/admin/" or
 http.request.uri.path starts_with "/api/super-admin/")
and not ip.src in {[OFFICE_IP/32] [VPN_IP/32]}
```

**Action**: Block

> Replace `[OFFICE_IP]` and `[VPN_IP]` with your actual allowlisted IPs.

### 5.2 — Block Suspicious User Agents

```
(http.user_agent contains "sqlmap" or
 http.user_agent contains "nikto" or
 http.user_agent contains "masscan" or
 http.user_agent contains "zgrab" or
 http.user_agent wildcard "*scanner*")
```

**Action**: Block

### 5.3 — Block TRACFIN/Compliance Endpoints

```
http.request.uri.path contains "/api/admin/tracfin"
```

**Applies after admin IP rule above — double protection for compliance endpoints.**

### 5.4 — Challenge Tor Exit Nodes

```
ip.geoip.is_in_european_union eq false and
http.request.uri.path starts_with "/api/flash-credit"
```

**Action**: Managed Challenge (CAPTCHA)

---

## 6. Managed Rulesets

Enable the following in Security → WAF → Managed rules:

- ✅ **Cloudflare Managed Ruleset** (OWASP Core)
- ✅ **Cloudflare OWASP Core Ruleset** — Paranoia Level: 2
- ✅ **Cloudflare Exposed Credentials Check**
- ✅ **Cloudflare Free Managed Ruleset**

---

## 7. DDoS Protection Settings

- **HTTP DDoS**: Enabled (High sensitivity)
- **L3/L4 DDoS**: Automatic (Cloudflare Magic Transit not required at this scale)
- **Under Attack Mode**: Available via API or dashboard when needed

---

## 8. Additional Security Headers (via Transform Rules)

Add these response headers via **Rules → Transform Rules → Modify Response Header**:

```
Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: camera=(), microphone=(), geolocation=()
```

> Note: The backend `SecurityHeadersFilter.java` already sets these for API responses.
> The Cloudflare rule adds them to ALL responses (including the web SPA).

---

## 9. Page Rules / Cache Rules

### 9.1 — Don't cache API responses

**URL pattern**: `flowguard.fr/api/*`
**Cache level**: Bypass

### 9.2 — Cache static web assets

**URL pattern**: `flowguard.fr/*.{js,css,png,svg,woff2}`
**Cache level**: Cache Everything
**Edge TTL**: 1 month

---

## 10. Terraform Reference (Cloudflare Provider)

```hcl
# Provider setup
terraform {
  required_providers {
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 4"
    }
  }
}

provider "cloudflare" {
  api_token = var.cloudflare_api_token
}

variable "zone_id"            { type = string }  # Cloudflare Zone ID
variable "cloudflare_api_token" { type = string, sensitive = true }
variable "office_ip"          { type = string }

# Rate limit — login
resource "cloudflare_rate_limit" "login_rate_limit" {
  zone_id   = var.zone_id
  threshold = 10
  period    = 60
  match {
    request {
      url_pattern = "*/api/auth/login*"
      schemes     = ["HTTPS"]
      methods     = ["POST"]
    }
  }
  action {
    mode    = "ban"
    timeout = 600
    response {
      content_type = "application/json"
      body         = "{\"error\":\"Too many login attempts. Please try again later.\"}"
    }
  }
  description = "flowguard-auth-rate-limit"
}

# WAF custom rule — block admin from non-allowlisted IPs
resource "cloudflare_filter" "admin_ip_allowlist" {
  zone_id     = var.zone_id
  description = "Admin endpoints IP allowlist"
  expression  = "(http.request.uri.path starts_with \"/api/admin/\") and not ip.src eq ${var.office_ip}"
}

resource "cloudflare_firewall_rule" "block_admin_external" {
  zone_id     = var.zone_id
  description = "Block admin endpoints from non-allowlisted IPs"
  filter_id   = cloudflare_filter.admin_ip_allowlist.id
  action      = "block"
  priority    = 1
}
```

---

## 11. Monitoring & Alerting

In Cloudflare dashboard → **Notifications**, configure alerts for:

- ✅ DDoS Attack Alert (L7)
- ✅ WAF Alert (anomaly spikes)
- ✅ Rate Limit Alert (threshold crossings)
- ✅ Firewall Events Alert

**Webhook**: Configure Cloudflare notifications to forward to PagerDuty or Slack.

---

## 12. Incident Response

| Trigger                       | Action                                         |
| ----------------------------- | ---------------------------------------------- |
| DDoS > 10k rps                | Enable "Under Attack Mode" via Cloudflare API  |
| Sustained attack on /api/auth | Temporarily block the source ASN               |
| Credential stuffing detected  | Enable "Exposed Credentials Check" + force MFA |

**Cloudflare API — Enable Under Attack Mode:**

```bash
curl -X PATCH "https://api.cloudflare.com/client/v4/zones/{zone_id}/settings/security_level" \
  -H "Authorization: Bearer $CF_API_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{"value":"under_attack"}'
```
