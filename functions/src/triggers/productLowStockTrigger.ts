import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions/v2";
import * as admin from "firebase-admin";
import { Product } from "../types";

/**
 * Cloud Function that triggers when a product is updated.
 * Sends a push notification when stock crosses below minimumQuantity threshold.
 * Guard: Only acts if stock field changed AND crossed the threshold from above to below.
 */
export const onProductLowStock = onDocumentUpdated(
  {
    document: "products/{productId}",
    region: "southamerica-east1",
    timeoutSeconds: 60
  },
  async (event) => {
    const before = event.data?.before.data() as Product;
    const after = event.data?.after.data() as Product;
    const productId = event.params.productId;

    if (!before || !after) {
      logger.warn(`Product ${productId}: Missing before or after data, skipping notification`);
      return;
    }

    if (!productId || productId.trim() === "") {
      logger.warn("Product ID is empty, skipping notification");
      return;
    }

    const beforeStock = before.quantity;
    const afterStock = after.quantity;
    
    if (beforeStock === afterStock) {
      return;
    }

    const wasAboveThreshold = beforeStock >= before.minimumQuantity;
    const isNowBelowThreshold = afterStock < after.minimumQuantity;

    if (!wasAboveThreshold || !isNowBelowThreshold) {
      return;
    }

    const productName = after.name?.trim() || "Producto sin nombre";
    if (!productName || productName === "") {
      logger.warn(`Product ${productId}: Product name is empty, skipping notification`);
      return;
    }

    const title = "⚠️ Stock Bajo";
    const body = `${productName}: Stock actual ${afterStock}, mínimo ${after.minimumQuantity}`;

    if (!title || !body || body.trim() === "") {
      logger.warn(`Product ${productId}: Notification title or body is empty, skipping notification`);
      return;
    }

    try {
      await admin.messaging().send({
        topic: "low_stock",
        notification: {
          title: title,
          body: body
        },
        data: {
          productId: productId,
          screen: "product_detail"
        },
        android: {
          priority: "high" as const,
          notification: {
            channelId: "estacion_dulce_general"
          }
        }
      });
      
      logger.info(`Product ${productId} (${productName}): stock ${beforeStock} → ${afterStock} (min: ${after.minimumQuantity}), push sent`);
    } catch (error) {
      logger.error(`Error sending low stock notification for product ${productId}:`, error);
      throw error;
    }
  }
);

