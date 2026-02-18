# Azure Load Testing Plugin for Apache JMeter

A JMeter plugin that lets you trigger load tests on [Azure Load Testing](https://learn.microsoft.com/en-us/azure/load-testing/) directly from the JMeter GUI.

## Features

- **Tools menu integration** — Adds a **"Run on Azure Load Testing"** item under the JMeter **Tools** menu
- **Azure authentication** — Supports Azure CLI, environment variables, managed identity, and interactive browser login via the Azure Identity SDK
- **Resource discovery** — Lists all Azure Load Testing resources you have access to across subscriptions
- **One-click test run** — Uploads the currently open JMX file and starts a test run on the selected resource

## Prerequisites

- Apache JMeter 5.6+ installed
- Java 17+
- An Azure subscription with at least one Azure Load Testing resource
- Azure CLI logged in (`az login`), or other Azure credential configured

## Building

```bash
./gradlew build
```

This produces a shadow/fat JAR at:

```
build/libs/jmeter-azure-load-testing-plugin-1.0.0.jar
```

The shadow JAR bundles all Azure SDK dependencies so only a single file needs to be deployed.

## Installation

1. Build the plugin (see above)
2. Copy the JAR to your JMeter installation's `lib/ext` directory:

```bash
cp build/libs/jmeter-azure-load-testing-plugin-1.0.0.jar $JMETER_HOME/lib/ext/
```

3. Restart JMeter

## Usage

1. Open a JMX test plan in JMeter (or create a new one and save it)
2. Go to **Tools → Run on Azure Load Testing**
3. Authenticate with Azure (a browser window opens if needed)
4. Select an Azure Load Testing resource from the list
5. Optionally edit the test name
6. Click **Run Load Test**
7. Monitor the test run in the [Azure Portal](https://portal.azure.com)

## How It Works

```
┌─────────────────────────────────────────────────────┐
│  JMeter GUI                                         │
│  ┌───────────────────────────────────────────────┐  │
│  │  Tools → "Run on Azure Load Testing"          │  │
│  └──────────────────┬────────────────────────────┘  │
│                     │                                │
│                     ▼                                │
│  ┌──────────────────────────────────────────────┐   │
│  │  Azure Auth (DefaultCredential / Browser)     │   │
│  └──────────────────┬───────────────────────────┘   │
│                     │                                │
│                     ▼                                │
│  ┌──────────────────────────────────────────────┐   │
│  │  List Azure Load Testing Resources (ARM API)  │   │
│  └──────────────────┬───────────────────────────┘   │
│                     │                                │
│                     ▼                                │
│  ┌──────────────────────────────────────────────┐   │
│  │  Resource Selection Dialog                    │   │
│  │  ┌────────────────────────────────────────┐   │   │
│  │  │  • my-load-test (rg-1 / eastus)        │   │   │
│  │  │  • perf-test (rg-2 / westus2)          │   │   │
│  │  └────────────────────────────────────────┘   │   │
│  │  [Run Load Test]  [Cancel]                    │   │
│  └──────────────────┬───────────────────────────┘   │
│                     │                                │
│                     ▼                                │
│  ┌──────────────────────────────────────────────┐   │
│  │  Create Test → Upload JMX → Start Test Run    │   │
│  │  (Azure Load Testing Data-Plane API)          │   │
│  └──────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

## Project Structure

```
src/main/java/org/apache/jmeter/protocol/azure/
├── gui/
│   ├── action/
│   │   └── AzureLoadTestAction.java      # Menu item + Command entry point
│   ├── AzureLoadTestDialog.java          # Resource selection dialog
│   └── LoadTestResourceListCellRenderer.java
└── service/
    ├── AzureAuthService.java             # Azure authentication
    ├── AzureLoadTestingClient.java       # ARM + data-plane API client
    └── LoadTestResource.java             # Resource model
```

## License

Licensed under the Apache License, Version 2.0.
