# Changelog

## 1.0.0 (2026-02-19)

### Features

- **Run menu integration** — "Run on Azure Load Testing" menu item under JMeter's **Run** menu
- **Azure authentication** — Supports Azure CLI, environment variables, managed identity, and interactive browser login via the Azure Identity SDK
- **Subscription & resource discovery** — Lists all Azure subscriptions and Load Testing resources the user has access to
- **Create new Load Testing resource** — Dialog to create a new resource group and Azure Load Testing resource by specifying a name and region
- **One-click test run** — Uploads the currently open JMX file, creates a test, and starts a test run on the selected resource
- **Live dashboard** — Real-time test run viewer with auto-refreshing metrics:
  - **Status** — Current test run state (Provisioning, Executing, Done, etc.)
  - **Duration** — Continuous 1-second timer showing elapsed time
  - **Virtual Users** — Live count from the Azure Metrics API
  - **Request Metrics** — Total requests, successful, failed, requests/sec, error %
  - **Response Times** — Average, p90, p95, p99 (milliseconds)
- **Live metrics via Metrics API** — Fetches `VirtualUsers`, `ResponseTime`, `TotalRequests`, and `Errors` metrics in real time during test execution
- **Post-completion statistics** — Parses `testRunStatistics` from the test run response for final results after completion
- **Cancel test run** — Cancel a running test and continue polling until the run reaches a terminal state
- **Portal URL link** — Clickable link to open the test run directly in the Azure portal
- **Custom cell renderer** — Rich list display for Load Testing resources showing name, resource group, and region
