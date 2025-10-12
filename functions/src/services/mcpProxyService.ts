import { logger } from "firebase-functions/v2";

/**
 * MCP JSON-RPC Request
 */
interface MCPRequest {
  jsonrpc: "2.0";
  method: string;
  params?: any;
  id: string | number;
}

/**
 * MCP JSON-RPC Response
 */
interface MCPResponse {
  jsonrpc: "2.0";
  result?: any;
  error?: {
    code: number;
    message: string;
    data?: any;
  };
  id: string | number;
}

/**
 * Call MCP server via JSON-RPC
 * ETags come in the payload, not headers
 * @param mcpServerUrl MCP server base URL
 * @param mcpApiKey API key for authentication
 * @param method JSON-RPC method (resources/read, tools/call, etc.)
 * @param params Method parameters (including ifNoneMatch if needed)
 * @param vercelBypassToken Optional Vercel bypass token for deployment protection
 * @returns MCP response with potential notModified flag and metadata in payload
 */
export async function callMCPServer(
  mcpServerUrl: string,
  mcpApiKey: string,
  method: string,
  params: any,
  vercelBypassToken?: string
): Promise<any> {
  try {
    const requestBody: MCPRequest = {
      jsonrpc: "2.0",
      method: method,
      params: params,
      id: Date.now()
    };

    const headers: Record<string, string> = {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${mcpApiKey}`
    };

    if (vercelBypassToken) {
      headers["x-vercel-protection-bypass"] = vercelBypassToken;
    }

    logger.info("MCP request", {
      url: mcpServerUrl,
      method: method,
      hasApiKey: !!mcpApiKey,
      hasVercelBypass: !!vercelBypassToken
    });

    const response = await fetch(mcpServerUrl, {
      method: "POST",
      headers: headers,
      body: JSON.stringify(requestBody)
    });

    if (!response.ok) {
      const errorText = await response.text();
      logger.error("MCP server error", {
        status: response.status,
        error: errorText
      });
      throw new Error(`MCP server error: ${response.status}`);
    }

    const data = await response.json() as MCPResponse;

    if (data.error) {
      logger.error("MCP JSON-RPC error", data.error);
      throw new Error(`MCP error: ${data.error.message}`);
    }

    return data.result;

  } catch (error) {
    logger.error("Error calling MCP server", error);
    throw error;
  }
}

