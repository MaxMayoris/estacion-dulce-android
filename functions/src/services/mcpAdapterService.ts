/**
 * Convert MCP tools to OpenAI function calling format
 * @param mcpTools Tools from MCP tools/list
 * @returns OpenAI function definitions
 */
export function convertMCPToolsToOpenAIFunctions(mcpTools: any[]): any[] {
  if (!mcpTools || mcpTools.length === 0) {
    return [];
  }

  return mcpTools.map((tool: any) => ({
    type: "function",
    function: {
      name: tool.name,
      description: tool.description || `MCP tool: ${tool.name}`,
      parameters: tool.inputSchema || {
        type: "object",
        properties: {},
        required: []
      }
    }
  }));
}

/**
 * Convert MCP resources to OpenAI function calling format
 * @param mcpResources Resources from MCP resources/list
 * @returns OpenAI function definitions for reading resources
 */
export function convertMCPResourcesToOpenAIFunctions(mcpResources: any[]): any[] {
  if (!mcpResources || mcpResources.length === 0) {
    return [];
  }

  return mcpResources.map((resource: any) => ({
    type: "function",
    function: {
      name: `read_${resource.uri.replace(/[^a-zA-Z0-9]/g, "_")}`,
      description: resource.description || `Read MCP resource: ${resource.uri}`,
      parameters: {
        type: "object",
        properties: {
          ifNoneMatch: {
            type: "string",
            description: "ETag for conditional read (optional)"
          }
        },
        required: []
      },
      metadata: {
        mcpUri: resource.uri,
        mcpMimeType: resource.mimeType
      }
    }
  }));
}

/**
 * Determine if function call is a resource read or tool call
 * @param functionName Function name from OpenAI
 * @param allFunctions All available functions with metadata
 * @returns { isResource: boolean, mcpUri?: string }
 */
export function analyzeFunctionCall(functionName: string, allFunctions: any[]): any {
  const func = allFunctions.find((f: any) => f.function.name === functionName);
  
  if (func?.function?.metadata?.mcpUri) {
    return {
      isResource: true,
      mcpUri: func.function.metadata.mcpUri
    };
  }
  
  return {
    isResource: false,
    toolName: functionName
  };
}

