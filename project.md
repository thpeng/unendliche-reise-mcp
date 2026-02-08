# PROJECT.md

## API
- All API fragements such as xsd, wsdl or openapi.yaml reside below src/main/generated. 
- Do not change any API files. 

## Specification
- Work with spec-driven development. First step is always to create specification in the folder src/main/specs
- the specs are written in markdown, must contain all business and non-functional requirements

## Technology Stack
- **Language**: Java
- **Framework**: Spring Boot with WebFlux (reactive)
- **AI Integration**: Spring AI
- **Build**: Maven
- **MCP Protocol**: Streamable HTTP (spec 2025-03-26)
- **Java Version**: [specify if you have preference, e.g., 17, 21]

## Spring AI Configuration
- **Dependency**: `spring-ai-starter-mcp-server-webflux`
- **Property**: `spring.ai.mcp.server.protocol=STREAMABLE`

## Project Configuration
- **Root Package**: `ch.thp.proto.unendlichereise`

## Architecture Principles

### Package Structure: Domain-Based
Packages are organized by domain/capability, not technical layer.

**Structure**:
```
ch.thp.proto.unendlichereise/
  ├── shared/              # Cross-cutting concerns only
  │   ├── ojp/
  │   ├── models/
  │   ├── validation/
  │   └── security/
  ├── toolname1/          # One package per MCP tool
  │   ├── Config
  │   ├── Service
  │   ├── Controller
  │   └── Models
  └── toolname2/
      └── ...
```

**Rules**:
- Each MCP tool lives in its own package
- All related code (config, service, models) stays together
- Shared objects go in `shared/` package
- No cross-domain dependencies except through `shared/`

### API Abstraction
MCP tools must abstract underlying API complexity into high-level, LLM-friendly operations.

**Design principles**:
- Tools expose intent-based operations, not raw API calls
- Single tool call should complete a meaningful user task
- Hide pagination, retries, error handling from LLM
- Return only essential information, filter noise
- Use clear, domain-specific naming

**Example**: Instead of `listItems(page, size, filter)` + `getItemDetails(id)`, provide `findRelevantItems(criteria)` that handles both internally.

## Security Requirements

### Input Validation
**All tool inputs are untrusted and potentially malicious.**

**Mandatory checks**:
- Validate all parameters against strict schemas
- Enforce length limits on strings
- Whitelist allowed characters where applicable
- Reject unexpected data types or formats
- Sanitize inputs before passing to external APIs

### Prompt Injection Defense
**Tool descriptions and responses must not be exploitable.**

**Protection measures**:
- Never include user input directly in tool descriptions
- Never echo unvalidated user input in responses
- Filter out instruction-like patterns from inputs
- Escape special characters in returned data
- Log suspicious input patterns

**Forbidden patterns in inputs**:
- Multi-line instructions
- Role-playing attempts ("ignore previous", "you are now")
- Attempts to extract system information
- Encoding tricks (base64, hex, unicode escapes)

### Authorization & Access Control
- Validate permissions before executing operations
- Implement least-privilege access patterns
- Never expose sensitive data in error messages
- Rate-limit tool executions if applicable

## Token Budget Constraints

### MCP Server Token Usage
**Hard limit**: Maximum 2000 tokens for entire MCP server integration (instructions + all tool schemas combined)

**Per tool budget**:
- Tool name + description: ~50 tokens
- Input schema: ~100-200 tokens
- Instructions: ~100-150 tokens

**Optimization requirements**:
- Use concise, direct language
- Avoid redundant descriptions
- Minimize schema complexity
- Reuse shared types

### Documentation Token Usage
- Keep .md files minimal and focused
- No redundancy between files
- No boilerplate or filler text
- Direct, actionable content only

## Prompt Design

### Target Compatibility
Prompts and instructions must work effectively with:
- **High-end**: Claude, GPT-4 class models
- **Low-end**: 8B parameter models (Llama 3.1 8B, Mistral 7B)

### Requirements
- Use simple, clear language
- Avoid complex reasoning chains
- Provide explicit instructions
- Use concrete examples over abstractions
- Keep context requirements minimal

## Testing Strategy

### Local Testing with Mocks
- All external dependencies must be mockable
- Tests run without network/external services
- Use DSL for test scenarios where applicable

### Security Testing
- Test input validation with malicious inputs
- Verify prompt injection defenses
- Validate error handling doesn't leak information
- Test rate limiting if implemented

### Test Structure
```java
@WebFluxTest
class ToolNameTest {
    // Mock external dependencies
    // DSL-based test scenarios
    // Security test cases
    // Verify MCP protocol compliance
}
```
- Always start the implementation with generating test cases, then implementation (TDD)
- Aim for 80% coverability

## Development Guidelines

### What Claude Code Should Handle
- Implementing tool logic within established structure
- Creating tests following mock patterns
- Generating schemas within token budget
- Writing tool descriptions under token limits
- Implementing input validation

### What to Review Manually
- Total token count verification
- Cross-tool consistency
- Shared model extraction opportunities
- 8B model compatibility of instructions
- Security vulnerability assessment