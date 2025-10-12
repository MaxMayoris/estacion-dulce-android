import { logger } from "firebase-functions/v2";
import { callMCPServer } from "./mcpProxyService";
import { analyzeFunctionCall } from "./mcpAdapterService";
import { OpenAIChatMessage } from "../types";

const MAX_ITERATIONS = 5;

/**
 * Agent Loop: OpenAI decides which tools to use, we execute them via MCP
 * @param apiKey OpenAI API key
 * @param userMessage User's question
 * @param systemPrompt System instructions
 * @param context Optional context
 * @param openAITools Tools in OpenAI function format
 * @param mcpServerUrl MCP server URL
 * @param mcpApiKey MCP API key
 * @param vercelBypassToken Vercel bypass token
 * @returns Final response and usage
 */
export async function runAgentLoop(
  apiKey: string,
  userMessage: string,
  systemPrompt: string,
  context: string | undefined,
  openAITools: any[],
  mcpServerUrl: string,
  mcpApiKey: string,
  vercelBypassToken: string
): Promise<{ reply: string; usage: { inputTokens: number; outputTokens: number; totalTokens: number }; mcpMetadata?: any }> {
  
  const messages: OpenAIChatMessage[] = [
    {
      role: "system",
      content: systemPrompt
    },
    {
      role: "user",
      content: context ? `${context}\n\nUser question: ${userMessage}` : userMessage
    }
  ];

  let totalUsage = {
    inputTokens: 0,
    outputTokens: 0,
    totalTokens: 0
  };

  let mcpMetadata: any = null;

  for (let iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
    logger.info(`Agent loop iteration ${iteration + 1}`);

    const response = await fetch("https://api.openai.com/v1/chat/completions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${apiKey}`
      },
      body: JSON.stringify({
        model: "gpt-4o-mini",
        messages: messages,
        max_tokens: 1000,
        temperature: 0.7,
        tools: openAITools,
        tool_choice: "auto"
      })
    });

    if (!response.ok) {
      throw new Error(`OpenAI error: ${response.status}`);
    }

    const data = await response.json();
    const message = data.choices[0]?.message;
    const usage = {
      inputTokens: data.usage.prompt_tokens,
      outputTokens: data.usage.completion_tokens,
      totalTokens: data.usage.total_tokens
    };
    const reply = message?.content?.trim() || "";

    totalUsage.inputTokens += usage.inputTokens;
    totalUsage.outputTokens += usage.outputTokens;
    totalUsage.totalTokens += usage.totalTokens;

    if (!message?.tool_calls || message.tool_calls.length === 0) {
      logger.info(`Agent loop completed after ${iteration + 1} iterations`);
      return { reply, usage: totalUsage, mcpMetadata };
    }

    messages.push(message);

    for (const toolCall of message.tool_calls) {
      const functionName = toolCall.function.name;
      const functionArgs = JSON.parse(toolCall.function.arguments || "{}");

      const analysis = analyzeFunctionCall(functionName, openAITools);

      logger.info(`Executing function: ${functionName}`, {
        isResource: analysis.isResource
      });

      try {
        let result;

        if (analysis.isResource) {
          result = await callMCPServer(
            mcpServerUrl,
            mcpApiKey,
            "resources/read",
            {
              uri: analysis.mcpUri,
              ifNoneMatch: functionArgs.ifNoneMatch
            },
            vercelBypassToken
          );
        } else {
          result = await callMCPServer(
            mcpServerUrl,
            mcpApiKey,
            "tools/call",
            {
              name: functionName,
              arguments: functionArgs
            },
            vercelBypassToken
          );
        }

        if (result?.etag) {
          mcpMetadata = {
            etag: result.etag,
            lastModified: result.lastModified,
            dataVersion: result.dataVersion,
            resourcesUsed: result.resourcesUsed
          };
        }

        const resultContent = result?.content?.[0]?.text 
          || result?.contents?.[0]?.text
          || JSON.stringify(result);

        messages.push({
          role: "tool",
          tool_call_id: toolCall.id,
          name: functionName,
          content: resultContent
        });

      } catch (error) {
        logger.error(`Error executing ${functionName}`, error);
        messages.push({
          role: "tool",
          tool_call_id: toolCall.id,
          name: functionName,
          content: `Error: ${error}`
        });
      }
    }
  }

  logger.warn(`Agent loop reached max iterations (${MAX_ITERATIONS})`);
  return {
    reply: "Lo siento, no pude procesar tu consulta completamente.",
    usage: totalUsage,
    mcpMetadata
  };
}

