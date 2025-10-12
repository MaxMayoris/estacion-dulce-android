import { logger } from "firebase-functions/v2";
import { OpenAIRequest, OpenAIResponse } from "../types";

const OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

/**
 * Calls OpenAI Chat Completions API
 * @param apiKey OpenAI API key from secrets
 * @param prompt User's message/prompt
 * @param systemPrompt System instructions for AI behavior
 * @param context Additional context data (optional)
 * @param tools MCP tool configuration (optional)
 * @returns AI response text and token usage
 */
export async function callOpenAI(
  apiKey: string,
  prompt: string,
  systemPrompt: string,
  context?: string,
  tools?: any[]
): Promise<{ reply: string; usage: { inputTokens: number; outputTokens: number; totalTokens: number }; message?: any }> {
  try {
    const messages = [
      {
        role: "system" as const,
        content: systemPrompt
      },
      {
        role: "user" as const,
        content: context ? `${context}\n\nConsulta del usuario: ${prompt}` : prompt
      }
    ];

    const requestBody: OpenAIRequest = {
      model: "gpt-4o-mini",
      messages: messages,
      max_tokens: 1000,
      temperature: 0.7
    };

    if (tools && tools.length > 0) {
      requestBody.tools = tools;
      requestBody.tool_choice = "auto";
    }

    const response = await fetch(OPENAI_API_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${apiKey}`
      },
      body: JSON.stringify(requestBody)
    });

    if (!response.ok) {
      const errorText = await response.text();
      logger.error("OpenAI API error", {
        status: response.status,
        statusText: response.statusText,
        error: errorText
      });
      throw new Error(`OpenAI API error: ${response.status} ${response.statusText}`);
    }

    const data = await response.json() as OpenAIResponse;

    const message = data.choices[0]?.message;
    const reply = message?.content?.trim() || "";
    const usage = {
      inputTokens: data.usage.prompt_tokens,
      outputTokens: data.usage.completion_tokens,
      totalTokens: data.usage.total_tokens
    };

    return { reply, usage, message };

  } catch (error) {
    logger.error("Error calling OpenAI API", error);
    throw error;
  }
}

