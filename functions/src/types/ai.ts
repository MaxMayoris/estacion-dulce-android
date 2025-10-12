/**
 * Request payload for AI Gateway
 */
export interface AIGatewayRequest {
  chatId: string;
  userMessage: string;
  context?: string;
  requestId: string;
  ifNoneMatch?: string;
  ifModifiedSince?: string;
  isFirstMessage?: boolean;
}

/**
 * Response payload from AI Gateway
 */
export interface AIGatewayResponse {
  reply: string;
  usage: {
    inputTokens: number;
    outputTokens: number;
    totalTokens: number;
  };
  requestId: string;
  notModified?: boolean;
  mcpMetadata?: {
    etag?: string;
    lastModified?: string;
    dataVersion?: string;
    resourcesUsed?: string[];
  };
}

/**
 * OpenAI Chat Message format
 */
export interface OpenAIChatMessage {
  role: "system" | "user" | "assistant" | "tool";
  content: string | null;
  tool_calls?: Array<{
    id: string;
    type: "function";
    function: {
      name: string;
      arguments: string;
    };
  }>;
  tool_call_id?: string;
  name?: string;
}

/**
 * MCP Tool Configuration
 */
export interface MCPToolConfig {
  type: "mcp";
  server_url: string;
  headers: {
    Authorization: string;
  };
  allowed_tools: string[];
  tool_choice: "auto" | "required" | "none";
}

/**
 * OpenAI API Request
 */
export interface OpenAIRequest {
  model: string;
  messages: OpenAIChatMessage[];
  max_tokens: number;
  temperature: number;
  tools?: any[];
  tool_choice?: "auto" | "required" | "none";
}

/**
 * OpenAI API Response
 */
export interface OpenAIResponse {
  id: string;
  object: string;
  created: number;
  model: string;
  choices: Array<{
    index: number;
    message: OpenAIChatMessage;
    finish_reason: "stop" | "tool_calls" | "length" | "content_filter";
  }>;
  usage: {
    prompt_tokens: number;
    completion_tokens: number;
    total_tokens: number;
  };
}


