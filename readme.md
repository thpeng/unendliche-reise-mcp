# 🚂 unendliche-reise-mcp

**A Swiss public transport MCP server — built on open data, hardened by paranoia.**

Named after Michael Ende's *Die unendliche Geschichte*, because Swiss train connections never end — and neither does the journey of getting MCP security right.

Successor to [`chattender-fahrplan-mcp`](https://github.com/thpeng/chattender-fahrplan-mcp). Same idea (let LLMs plan train journeys in Switzerland), rebuilt from scratch with different goals.

## What changed

| | chattender-fahrplan-mcp | unendliche-reise-mcp                                                                          |
|---|---|-----------------------------------------------------------------------------------------------|
| **Data source** | Proprietary SBB APIs | [opentransportdata.swiss](https://opentransportdata.swiss) (fully open data)                  |
| **MCP spec** | Basic tool calls | Latest spec incl. elicitation flows                                                           |
| **Security posture** | Naive | Testbed for defenses against [lokis-mcp](https://github.com/thpeng/lokis-mcp) attack patterns |
| **Dev approach** | Thesis PoC | Spec-driven development with Claude Code                                                      |

## Why this exists

**1. Open data, no strings attached.**
The original used proprietary SBB APIs. This version runs entirely on [opentransportdata.swiss](https://opentransportdata.swiss) — Switzerland's open transport data platform. No API keys from internal systems, no access restrictions. Anyone can run this.

**2. Elicitation done right.**
When a user asks for "Zürich → Basel" and there are multiple stations matching "Zürich", the server doesn't guess — it uses MCP's elicitation capability to ask the user. This is what the spec intended, but most implementations skip it.

**3. Hardening against real attack vectors.**
[`lokis-mcp`](https://github.com/thpeng/lokis-mcp) demonstrates how MCP tools can be weaponized — tool shadowing, data exfiltration, rug pulls. This project is the other side of that coin: a real-world MCP server that incorporates lessons learned and acts as a testbed for defenses. Browsers solved this decades ago with origin isolation, CSP, permission prompts, and sandboxed iframes. MCP clients haven't caught up yet.

**4. Spec-driven development.**
The OpenAPI specs from opentransportdata.swiss are the source of truth. This repo is a testbed for how well that workflow holds up.

## Architecture

Java 21 / Spring Boot with WebFlux (reactive) / Spring AI for MCP integration. Uses the Streamable HTTP transport from the 2025-03-26 MCP spec.

```
ch.thp.proto.unendlichereise/
  ├── shared/              # Cross-cutting: OJP client, models, validation, security
  ├── toolname1/           # One package per MCP tool (config, service, models)
  └── toolname2/
```

Packages are organized by domain, not technical layer. Each MCP tool is self-contained — all its config, service logic, and models live together. No cross-domain dependencies except through `shared/`.

### Design constraints

- **2000 token hard limit** for the entire MCP server integration (instructions + all tool schemas). Every tool description is budgeted at ~50 tokens, every schema at ~200. This forces you to be precise — and it's the only way to stay compatible with smaller models.
- **Multi-model compatibility.** Tool descriptions and prompts must work with both Claude/GPT-4 class models and 8B parameter models (Llama 3.1 8B, Mistral 7B). No complex reasoning chains, no implicit context.
- **Intent-based tool design.** Tools expose high-level operations, not raw API wrappers. A single tool call should complete a meaningful user task — pagination, retries, and noise filtering happen internally.
- **All inputs are untrusted.** Strict schema validation, character whitelisting, length limits. Prompt injection patterns (role-playing attempts, encoding tricks, multi-line instructions) are filtered and logged. Tool responses never echo unvalidated input.
- **TDD with mocked externals.** All external dependencies are mockable, tests run without network. Security test cases (malicious inputs, injection attempts, info leakage via errors) are first-class citizens. Target: 80% coverage.

see project.md for more details

## Quick start

```bash
git clone https://github.com/thpeng/unendliche-reise-mcp.git
cd unendliche-reise-mcp
./mvnw package
java -jar target/unendliche-reise-mcp-*.jar
```

Configure your MCP client (Claude Desktop, etc.) to point at the running server.

## See also

- [`chattender-fahrplan-mcp`](https://github.com/thpeng/chattender-fahrplan-mcp) — the predecessor
- [`lokis-mcp`](https://github.com/thpeng/lokis-mcp) — the adversary (MCP security workshop tool)

## License

MIT