# AutoLoc – Monitoring notes (Prometheus + Grafana)

## Architecture

```
autoloc-api (IntelliJ, port 8080)
   └── /actuator/prometheus   <- metrics in Prometheus format (Actuator + Micrometer)
            ▲
            │ scrape every 5 s (host.docker.internal:8080)
            │
Prometheus (Docker, port 9090)   <- collects and stores time series
            ▲
            │ PromQL queries (http://prometheus:9090, Docker service name)
            │
Grafana (Docker, port 3000)      <- dashboard "AutoLoc — Application Monitoring"
```

- **Spring Boot Actuator** exposes `/actuator/health` and `/actuator/prometheus`.
- **Micrometer Prometheus registry** (`micrometer-registry-prometheus`) formats the metrics so Prometheus can read them.
- **Prometheus** runs in Docker and scrapes the application every 5 seconds (`monitoring/prometheus.yml`). From inside a container, `localhost` is the container itself, so the host computer is reached with `host.docker.internal`.
- **Grafana** runs in Docker and reads Prometheus. Its data source URL is `http://prometheus:9090`: both containers are in the same Docker Compose network, so Grafana reaches Prometheus by its service name.
- Both containers are started with `docker compose up -d` (`docker-compose.yml` at the project root) and stopped with `docker compose down`.

Configuration added to the application:

- `pom.xml`: `spring-boot-starter-actuator` and `micrometer-registry-prometheus` (versions managed by Spring Boot).
- `application.properties`:
  ```properties
  management.endpoints.web.exposure.include=health,prometheus
  management.endpoint.health.show-details=always
  ```
  Only the two endpoints needed are exposed, not all Actuator endpoints.

## Dashboard panels

| Panel | PromQL query | Type | Why it is useful |
|---|---|---|---|
| JVM memory used | `jvm_memory_used_bytes` | Time series (bytes IEC) | Shows heap and non-heap memory by area. A heap that keeps growing without going down after garbage collection would point to a memory leak. |
| CPU usage | `process_cpu_usage` | Time series (percent 0.0-1.0) | CPU used by the Spring Boot process. Shows the load created by incoming requests. |
| HTTP request rate | `sum(rate(http_server_requests_seconds_count[1m]))` | Time series (req/s) | Number of HTTP requests per second handled by the API. Main indicator of traffic. |
| Live threads | `jvm_threads_live_threads` | Stat | Current number of JVM threads. A constant increase would indicate a thread leak. |

## Observations

- With no user traffic, **HTTP request rate is about 0.2 req/s**. These requests come from Prometheus itself: it calls `/actuator/prometheus` every 5 seconds, so 1 / 5 = 0.2 request per second.
- During the traffic test (a PowerShell loop sending repeated requests to `/api/public/ping` and, with the `student` account, to `/api/hello`), the request rate rose to **about 10 req/s**, CPU usage went from about 0.1 % to **about 6 %** at the peak, and the G1 Eden Space (young generation) memory rose to about 48 MiB then dropped: each request creates short-lived objects, and the garbage collector frees them regularly. CPU went back to almost 0 % as soon as the loop slowed down.
- Live threads stayed stable (**26 threads**): Tomcat reuses its thread pool instead of creating a new thread per request.

## Issue resolved: Prometheus target DOWN (HTTP 401)

**Symptom.** After starting Prometheus, the page Status → Targets showed the `autoloc-api` target **DOWN** with the error `server returned HTTP status 401`.

**Cause.** Spring Security was added to the project (`SecurityConfig`). The rule `.anyRequest().authenticated()` also applied to `/actuator/prometheus`. Prometheus sends its requests without credentials, so the application answered 401 Unauthorized.

**Fix.** The two monitoring endpoints were explicitly allowed, **before** the broader rules, because Spring Security applies the first matching rule:

```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
    .requestMatchers("/api/public/**").permitAll()
    .requestMatchers("/api/admin/**").hasRole("ADMIN")
    .requestMatchers("/api/**").hasAnyRole("USER", "ADMIN")
    .anyRequest().authenticated()
).httpBasic(basic -> {});
```

After restarting the application, the target became **UP (1/1 up)**.

**Limit.** This is acceptable for a local lab. In production, the metrics and detailed health information should not be public: they would be protected (scrape authentication in Prometheus, or a separate management port reachable only from the internal network).

## Default credentials

Grafana starts with `admin` / `admin` on a fresh container. The password must be changed at first login and default credentials must never be kept outside the classroom lab.
