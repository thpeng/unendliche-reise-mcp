{# ===================== TypeScript Type Rendering ===================== #}
{%- macro render_typescript_type(param_spec, required_params) -%}
    {%- if not param_spec or not (param_spec.type or param_spec.oneOf or param_spec.properties or param_spec.items) -%}
        {{- "any" -}}
    {%- elif param_spec.type == "array" -%}
        {%- set items = param_spec.items if param_spec.items is defined else none -%}
        {%- if items and items.type in ["string","number","integer","boolean"] -%}
            {{- (items.type == "integer") and "number[]" or (items.type ~ "[]") -}}
        {%- elif items -%}
            {{- render_typescript_type(items, required_params) ~ "[]" -}}
        {%- else -%}
            {{- "any[]" -}}
        {%- endif -%}
        {%- if param_spec.nullable -%}{{ " | null" }}{%- endif -%}
    {%- elif param_spec.type is defined and param_spec.type is iterable
          and param_spec.type is not string and param_spec.type is not mapping
          and (param_spec.type|length) > 0 -%}
        {{- (param_spec.type | join(" | ")) -}}
    {%- elif param_spec.oneOf -%}
        {%- set parts = [] -%}
        {%- for v in param_spec.oneOf -%}
            {%- set parts = parts + [ render_typescript_type(v, required_params) ] -%}
        {%- endfor -%}
        {{- parts | join(" | ") -}}
    {%- elif param_spec.type == "string" -%}
        {%- if param_spec.enum -%}
            {{- '"' ~ (param_spec.enum | join('" | "')) ~ '"' -}}
        {%- else -%}
            {{- "string" -}}
            {%- if param_spec.nullable -%}{{ " | null" }}{%- endif -%}
        {%- endif -%}
    {%- elif param_spec.type in ["number","integer"] -%}
        {{- "number" -}}
    {%- elif param_spec.type == "boolean" -%}
        {{- "boolean" -}}
    {%- elif param_spec.type == "object" -%}
        {%- if param_spec.properties -%}
            {{
"{"
            }}
            {%- for prop_name, prop_spec in param_spec.properties.items() -%}
                {{- prop_name -}}
                {%- set req = param_spec.required if param_spec.required is defined else [] -%}
                {%- if prop_name not in req -%}{{ "?" }}{%- endif -%}
                {{- ": " -}}
                {{- render_typescript_type(prop_spec, req) -}}
                {%- if not loop.last -%}{{ ", " }}{%- endif -%}
            {%- endfor -%}
            {{ "}" }}
        {%- else -%}
            {{- "object" -}}
        {%- endif -%}
    {%- else -%}
        {{- "any" -}}
    {%- endif -%}
{%- endmacro -%}

{# ===================== Tool Rendering (accepts both formats) ===================== #}
{%- macro render_tool(ts) -%}
    {# Normalize two shapes:
       A) { "type":"function", "function": { name, description, parameters } }
       B) { "type":"function", name, description, parameters }
    #}
    {%- set fn = (ts.function if ts.function is defined else ts) -%}
    {%- set tname = fn.name if fn.name is defined else "tool" -%}
    {%- set tdesc = fn.description if fn.description is defined else "" -%}
    {%- set params = fn.parameters if fn.parameters is defined else {} -%}

    {{- "// " ~ tdesc ~ "\n" -}}
    {{- "type " ~ tname ~ " = " -}}
    {%- if params and params.type == "object" and params.properties -%}
        {{- "(_: {\n" -}}
        {%- set req = params.required if params.required is defined else [] -%}
        {%- for pname, pspec in params.properties.items() -%}
            {%- if pspec.description is defined and pspec.description -%}
                {{- "// " ~ pspec.description ~ "\n" -}}
            {%- endif -%}
            {{- pname -}}
            {%- if pname not in req -%}{{ "?" }}{%- endif -%}
            {{- ": " -}}
            {{- render_typescript_type(pspec, req) -}}
            {%- if pspec.default is defined -%}
                {%- if pspec.enum -%}
                    {{- ", // default: " ~ pspec.default -}}
                {%- else -%}
                    {{- ", // default: " ~ pspec.default -}}
                {%- endif -%}
            {%- endif -%}
            {%- if not loop.last -%}{{ ",\n" }}{%- else -%}{{ "\n" }}{%- endif -%}
        {%- endfor -%}
        {{- "}) => any;" -}}
    {%- else -%}
        {{- "() => any;" -}}
    {%- endif -%}
{%- endmacro -%}

{%- macro render_tools(tools) -%}
    {%- if tools -%}
        {%- for t in tools -%}
            {{- render_tool(t) -}}
            {%- if not loop.last -%}{{ "\n" }}{%- endif -%}
        {%- endfor -%}
    {%- else -%}
        {{- "// No tools provided" -}}
    {%- endif -%}
{%- endmacro -%}

{# ===================== Tokens and State ===================== #}
{{ bos_token }}
{%- set system_token = '<|system_start|>' -%}
{%- set end_system_token = '<|system_end|>' -%}
{%- set developer_token = '<|developer_start|>' -%}
{%- set end_developer_token = '<|developer_end|>' -%}
{%- set user_token = '<|user_start|>' -%}
{%- set end_user_token = '<|user_end|>' -%}
{%- set assistant_token = '<|assistant_start|>' -%}
{%- set end_assistant_token = '<|assistant_end|>' -%}
{%- set inner_token = '<|inner_prefix|>' -%}
{%- set outer_token = '<|inner_suffix|>' -%}
{%- set tool_calls_token = '<|tools_prefix|>' -%}
{%- set end_tool_calls_token = '<|tools_suffix|>' -%}
{%- set ns = namespace(in_assistant=false, in_tool=false, in_inner=false, assistant_format=none) -%}

{# ===================== System Message (no function calls) ===================== #}
{%- if messages and messages[0].role == 'system' and ("content" in messages[0]) -%}
    {%- if messages[0].content is string -%}
        {{ system_token ~ messages[0].content ~ end_system_token }}
    {%- elif messages[0].content is mapping and "text" in messages[0].content -%}
        {{ system_token ~ messages[0].content.text ~ end_system_token }}
    {%- else -%}
        {{ system_token ~ 'Invalid system message' ~ end_system_token }}
    {%- endif -%}
    {%- set loop_messages = messages[1:] -%}
{%- else -%}
    {%- set today = current_date if current_date is defined else '2025-11-06' -%}
    {{ system_token ~ 'You are Apertus, a helpful assistant created by the SwissAI initiative.\nKnowledge cutoff: 2024-04\nCurrent date: ' ~ today ~ end_system_token }}
    {%- set loop_messages = messages -%}
{%- endif -%}

{# ===================== Developer Block ===================== #}
{{ developer_token ~ 'Deliberation: ' }}
{%- if enable_thinking is defined and enable_thinking -%}
    {{ 'enabled\n' }}
{%- else -%}
    {{ 'disabled\n' }}
{%- endif -%}
{%- if tools is defined and tools -%}
    {{ 'Tool Capabilities:\n' ~ render_tools(tools) }}
{%- else -%}
    {{ 'Tool Capabilities: disabled' }}
{%- endif -%}
{{ end_developer_token }}

{# ===================== Conversation Rendering ===================== #}
{%- for message in loop_messages -%}
    {%- if message.role == 'user' -%}
        {%- set ns.in_inner = false -%}
        {%- if ns.in_tool -%}{{ ']' }}{%- set ns.in_tool = false -%}{%- endif -%}
        {%- if ns.in_assistant -%}{{ end_assistant_token }}{%- set ns.in_assistant = false -%}{%- endif -%}
        {%- if "content" in message -%}
            {{ user_token }}
            {%- if message.content is string -%}
                {{ message.content }}
            {%- elif message.content is mapping and "parts" in message.content -%}
                {%- for part in message.content.parts -%}
                    {%- if part.type == "text" -%}{{ part.text }}{%- else -%}[Unsupported user part]{%- endif -%}
                {%- endfor -%}
            {%- else -%}
                [Invalid user message]
            {%- endif -%}
            {{ end_user_token }}
        {%- endif -%}

    {%- elif message.role == 'assistant' -%}
        {%- if not ns.in_assistant -%}{{ assistant_token }}{%- set ns.in_assistant = true -%}{%- endif -%}
        {%- if "content" in message -%}
            {%- if message.content is string and (ns.assistant_format is none or ns.assistant_format == "string") -%}
                {%- if ns.in_tool -%}{{ ']' }}{%- set ns.in_tool = false -%}{%- endif -%}
                {%- set ns.assistant_format = "string" -%}
                {{ message.content }}
            {%- elif message.content is mapping and "blocks" in message.content
                   and (ns.assistant_format is none or ns.assistant_format == "mapping") -%}
                {%- set ns.assistant_format = "mapping" -%}
                {%- for block in message.content.blocks -%}
                    {%- if block.type == 'thoughts' -%}
                        {%- if ns.in_tool -%}{{ ']' }}{%- set ns.in_tool = false -%}{%- endif -%}
                        {%- if not ns.in_inner -%}{%- set ns.in_inner = true -%}{{ inner_token }}{%- endif -%}
                        {{ block.text }}
                    {%- elif block.type == 'tool_calls' -%}
                        {%- if ns.in_tool -%}{{ ']' }}{%- set ns.in_tool = false -%}{%- endif -%}
                        {%- if ns.in_inner and not loop.first and block.calls|length == 1 and block.calls[0].name == 'display_answers' -%}
                            {%- set ns.in_inner = false -%}{{ outer_token }}
                        {%- endif -%}
                        {{ tool_calls_token ~ '[' }}
                        {%- for tc in block.calls -%}
                            {{- '{"' ~ (tc.name if tc.name is defined else 'tool') ~ '": ' ~ (tc.arguments if tc.arguments is defined else '{}') ~ '}' -}}
                            {%- if not loop.last -%}{{ ", " }}{%- endif -%}
                        {%- endfor -%}
                        {{ ']' ~ end_tool_calls_token }}
                    {%- elif block.type == 'tool_outputs' -%}
                        {%- if ns.in_tool -%}[Invalid: mixed tool outputs]{%- else -%}
                            {{ '[' }}{%- for to in block.outputs -%}{{ to.output }}{%- if not loop.last -%}{{ ", " }}{%- endif -%}{%- endfor -%}{{ ']' }}
                        {%- endif -%}
                    {%- elif block.type == 'response' -%}
                        {%- if ns.in_tool -%}{{ ']' }}{%- set ns.in_tool = false -%}{%- endif -%}
                        {%- if ns.in_inner -%}{%- set ns.in_inner = false -%}{{ outer_token }}{%- endif -%}
                        {{ block.text }}
                    {%- else -%}
                        [Invalid assistant block]
                    {%- endif -%}
                {%- endfor -%}
            {%- else -%}
                [Invalid assistant content]
            {%- endif -%}
        {%- else -%}
            [Invalid assistant message]
        {%- endif -%}

        {%- if "tool_calls" in message and message.tool_calls -%}
            {{ tool_calls_token ~ '[' }}
            {%- for tool_call in message.tool_calls -%}
                {%- if tool_call.type == 'function' and tool_call.function is defined -%}
                    {{- '{"' ~ tool_call.function.name ~ '": ' ~ (tool_call.function.arguments if tool_call.function.arguments is defined else '{}') ~ '}' -}}
                {%- else -%}
                    {{- '{"tool": {}}' -}}
                {%- endif -%}
                {%- if not loop.last -%}{{ ", " }}{%- endif -%}
            {%- endfor -%}
            {{ ']' ~ end_tool_calls_token }}
        {%- endif -%}

    {%- elif message.role == 'tool' -%}
        {%- if not ns.in_assistant -%}[Tool message outside of assistant]{%- endif -%}
        {%- if not ns.in_tool -%}{{ '[' }}{%- set ns.in_tool = true -%}{%- else -%}{{ ", " }}{%- endif -%}
        {{ message.content }}
    {%- else -%}
        [Invalid message role]
    {%- endif -%}
{%- endfor -%}

{%- if ns.in_tool -%}{{ ']' }}{%- endif -%}
{%- if add_generation_prompt -%}{{ assistant_token }}{%- endif -%}
