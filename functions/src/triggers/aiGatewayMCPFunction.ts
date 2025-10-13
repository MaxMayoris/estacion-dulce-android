import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { defineSecret, defineString } from "firebase-functions/params";
import { AIGatewayRequest, AIGatewayResponse } from "../types";
import { callMCPServer } from "../services/mcpProxyService";
import { convertMCPToolsToOpenAIFunctions, convertMCPResourcesToOpenAIFunctions } from "../services/mcpAdapterService";
import { runAgentLoop } from "../services/agentLoopService";

const OPENAI_API_KEY = defineSecret("OPENAI_API_KEY");
const MCP_API_KEY = defineSecret("MCP_API_KEY");
const VERCEL_BYPASS_TOKEN = defineSecret("VERCEL_BYPASS_TOKEN");
const MCP_SERVER_URL = defineString("MCP_SERVER_URL");

/**
 * In-memory cache for request idempotency
 * Stores requestId -> response for 60 seconds
 */
const requestCache = new Map<string, { response: AIGatewayResponse; timestamp: number }>();
const CACHE_TTL = 60000;

const mcpSessionCache = new Map<string, { initialized: boolean; toolsList: any; resourcesList: any }>();

/**
 * Cleans expired cache entries
 */
function cleanExpiredCache() {
  const now = Date.now();
  for (const [requestId, entry] of requestCache.entries()) {
    if (now - entry.timestamp > CACHE_TTL) {
      requestCache.delete(requestId);
    }
  }
}

/**
 * AI Gateway MCP Callable Function
 * Securely proxies requests from the app to OpenAI API with MCP server integration
 */
export const aiGatewayMCP = onCall(
  {
    region: "southamerica-east1",
    timeoutSeconds: 60,
    memory: "512MiB",
    secrets: [OPENAI_API_KEY, MCP_API_KEY, VERCEL_BYPASS_TOKEN],
    enforceAppCheck: false
  },
  async (request) => {
    if (!request.auth) {
      logger.warn("Unauthorized request to aiGatewayMCP - no auth token");
      throw new HttpsError(
        "unauthenticated",
        "Authentication required to use AI Gateway MCP"
      );
    }

    cleanExpiredCache();

    const data = request.data as AIGatewayRequest;

    if (!data.chatId || !data.userMessage || !data.requestId) {
      logger.warn("Invalid request data", { data });
      throw new HttpsError(
        "invalid-argument",
        "Missing required fields: chatId, userMessage, requestId"
      );
    }

    const cachedEntry = requestCache.get(data.requestId);
    if (cachedEntry) {
      return cachedEntry.response;
    }

    try {
      const apiKey = OPENAI_API_KEY.value();
      const mcpApiKey = MCP_API_KEY.value();
      const vercelBypassToken = VERCEL_BYPASS_TOKEN.value();
      const mcpServerUrl = MCP_SERVER_URL.value();
      
      if (!apiKey || apiKey.trim() === "") {
        logger.error("OPENAI_API_KEY secret is not configured");
        throw new HttpsError(
          "failed-precondition",
          "AI service is not properly configured"
        );
      }

      if (!mcpApiKey || mcpApiKey.trim() === "") {
        logger.error("MCP_API_KEY secret is not configured");
        throw new HttpsError(
          "failed-precondition",
          "MCP service is not properly configured"
        );
      }

      if (!mcpServerUrl || mcpServerUrl.trim() === "") {
        logger.error("MCP_SERVER_URL environment variable is not configured");
        throw new HttpsError(
          "failed-precondition",
          "MCP server URL is not configured"
        );
      }

      if (data.isFirstMessage && !mcpSessionCache.has(data.chatId)) {
        try {
          await callMCPServer(mcpServerUrl, mcpApiKey, "initialize", {
            protocolVersion: "2024-11-05",
            clientInfo: {
              name: "estacion-dulce-app",
              version: "1.0"
            }
          }, vercelBypassToken);

          const toolsList = await callMCPServer(mcpServerUrl, mcpApiKey, "tools/list", {}, vercelBypassToken);
          const resourcesList = await callMCPServer(mcpServerUrl, mcpApiKey, "resources/list", {}, vercelBypassToken);

          mcpSessionCache.set(data.chatId, {
            initialized: true,
            toolsList: toolsList,
            resourcesList: resourcesList
          });
        } catch (error) {
          logger.warn("MCP initialize failed", error);
        }
      }

      const mcpInstructions = "Use available tools for inventory/products/recipes/movements queries. Never invent data. TZ: America/Argentina/Buenos_Aires";

      const basePersonality = "Cha (virtual brother of Agostina, nicknames: Goni/Aguito, Estación Dulce): warm, caring, protective. Spanish replies, concise. Use nicknames Aguito or Goni occasionally, NEVER use hermanita/hermanito. Pastry/cooking expert. Use markdown for formatting (**, ##, lists). Format Name: qty, emojis occasional.";

      const systemPrompt = data.isFirstMessage 
        ? `${basePersonality}\n\n${mcpInstructions}`
        : basePersonality;

      const session = mcpSessionCache.get(data.chatId);
      const mcpTools = session?.toolsList?.tools || [];
      const mcpResources = session?.resourcesList?.resources || [];
      
      const openAIFunctions = [
        ...convertMCPToolsToOpenAIFunctions(mcpTools),
        ...convertMCPResourcesToOpenAIFunctions(mcpResources)
      ];

      const { reply, usage, mcpMetadata } = await runAgentLoop(
        apiKey,
        data.userMessage,
        systemPrompt,
        data.context,
        openAIFunctions,
        mcpServerUrl,
        mcpApiKey,
        vercelBypassToken
      );

      const response: AIGatewayResponse = {
        reply,
        usage,
        requestId: data.requestId,
        notModified: false,
        mcpMetadata
      };

      requestCache.set(data.requestId, {
        response,
        timestamp: Date.now()
      });

      return response;

    } catch (error) {
      logger.error("AI Gateway MCP error", error);
      throw new HttpsError(
        "internal",
        "Failed to process AI request. Please try again."
      );
    }
  }
);

