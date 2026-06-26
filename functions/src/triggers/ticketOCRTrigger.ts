import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { defineSecret } from "firebase-functions/params";
import * as admin from "firebase-admin";

const GEMINI_API_KEY = defineSecret("GEMINI_API_KEY");

interface ProductListItem {
  id: string;
  name: string;
}

/**
 * Callable function to process a shopping ticket image using Gemini 1.5 Flash.
 * Extracts items and matches them to Firestore products.
 */
export const processTicketOCR = onCall(
  {
    region: "southamerica-east1",
    timeoutSeconds: 60,
    memory: "512MiB",
    secrets: [GEMINI_API_KEY],
    enforceAppCheck: false
  },
  async (request) => {
    if (!request.auth) {
      logger.warn("Unauthorized request to processTicketOCR");
      throw new HttpsError(
        "unauthenticated",
        "Authentication required"
      );
    }

    const { imageBase64, mimeType } = request.data;
    if (!imageBase64 || !mimeType) {
      throw new HttpsError(
        "invalid-argument",
        "Missing imageBase64 or mimeType"
      );
    }

    const apiKey = GEMINI_API_KEY.value();
    if (!apiKey || apiKey.trim() === "") {
      logger.error("GEMINI_API_KEY is not configured");
      throw new HttpsError(
        "failed-precondition",
        "Gemini API key is not configured"
      );
    }

    try {
      const db = admin.firestore();

      // 1. Fetch products list from Firestore (only name field to optimize)
      const productsSnapshot = await db.collection("products").select("name").get();
      const productsList: ProductListItem[] = [];
      productsSnapshot.forEach((doc) => {
        const data = doc.data();
        productsList.push({
          id: doc.id,
          name: data.name || ""
        });
      });

      const currentYear = new Date().getFullYear();

      // 2. Construct Gemini Prompt
      const systemInstructions = `You are an expert expense parser for 'Estacion Dulce' bakery. 
Your goal is to parse a shopping ticket image, extract the items purchased (cost, quantity, name), and map each item to the closest available product from the provided list.

Here is the list of available Products:
${JSON.stringify(productsList)}

Rules for mapping:
1. Fuzzy-match the ticket item name to the Product names.
2. If it matches a product, set "collection" to "products" and "collectionId" to the product's ID. Set "customName" to null.
3. If there is absolutely no match in the list, set "collection" to "custom", "collectionId" to a unique code like "unmatched_x" (where x is an index), and set "customName" to the original raw name from the ticket.
4. Extracted costs and quantities must be numerical values.
5. Identify the total amount of the ticket and return it.
6. Identify the date of the transaction. If found, return it as 'date' in YYYY-MM-DD or YYYY-MM-DD HH:mm format. If the year is missing from the ticket, assume the current year (${currentYear}). If not found at all, set 'date' to null.

Return a JSON object only, matching the response schema.`;

      // 3. Call Gemini 3.5 Flash API
      const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=${apiKey}`;

      const payload = {
        contents: [
          {
            parts: [
              { text: systemInstructions },
              {
                inlineData: {
                  mimeType: mimeType,
                  data: imageBase64
                }
              }
            ]
          }
        ],
        generationConfig: {
          responseMimeType: "application/json",
          responseSchema: {
            type: "OBJECT",
            properties: {
              totalAmount: { type: "NUMBER", description: "The total amount of the transaction" },
              date: { type: "STRING", description: "The purchase date extracted from the ticket in YYYY-MM-DD or YYYY-MM-DD HH:mm format (null if not found)" },
              items: {
                type: "ARRAY",
                items: {
                  type: "OBJECT",
                  properties: {
                    collection: { type: "STRING", description: "products or custom" },
                    collectionId: { type: "STRING", description: "Matching database product ID or unmatched_x" },
                    customName: { type: "STRING", description: "Original raw name of item if unmatched (null otherwise)" },
                    cost: { type: "NUMBER", description: "Cost per unit" },
                    quantity: { type: "NUMBER", description: "Quantity of the item purchased" }
                  },
                  required: ["collection", "collectionId", "cost", "quantity"]
                }
              }
            },
            required: ["totalAmount", "items"]
          }
        }
      };

      const response = await fetch(geminiUrl, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        const errText = await response.text();
        logger.error("Gemini API request failed", { status: response.status, error: errText });
        throw new HttpsError("internal", `Gemini API failed: ${errText}`);
      }

      const result: any = await response.json();
      const text = result?.candidates?.[0]?.content?.parts?.[0]?.text;
      if (!text) {
        throw new Error("No output returned from Gemini");
      }

      const parsedResult = JSON.parse(text);
      return parsedResult;

    } catch (error: any) {
      logger.error("Error in processTicketOCR", error);
      throw new HttpsError(
        "internal",
        error.message || "Failed to process ticket OCR"
      );
    }
  }
);
