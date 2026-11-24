# Monitoring notes

## Architecture
- Spring Boot Actuator exposes /actuator/health and /actuator/prometheus
- Micrometer formats the metrics for Prometheus
- Prometheus (Docker) scrapes the app every 5s on host.docker.internal:8080
- Grafana (Docker) reads Prometheus and shows the dashboard "AutoLoc — Application Monitoring"

## Metrics in the dashboard
| Panel | Query | Why |
|---|---|---|
| JVM memory used | jvm_memory_used_bytes | see how much memory the app uses |
| CPU usage | process_cpu_usage | see the CPU load of the app |
| HTTP request rate | sum(rate(http_server_requests_seconds_count[1m])) | number of requests per second |
| Live threads | jvm_threads_live_threads | number of threads running |

When I sent 200 requests with curl, the request rate went from about 0.2 to 3 req/s and the CPU went up.

## Issue resolved
The p95 response time panel from the PDF didn't work because the metric http_server_requests_seconds_bucket doesn't exist (no histogram buckets). I used the 4 other panels instead.