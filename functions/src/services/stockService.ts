import * as admin from "firebase-admin";
import { logger } from "firebase-functions/v2";
import { Movement } from "../types";
import { getRecipeBOM } from "./bomService";

const db = admin.firestore();

/**
 * Calculate the stock delta for a movement
 */
export async function calculateMovementDelta(movement: Movement): Promise<{ [productId: string]: number }> {
  const delta: { [productId: string]: number } = {};

  for (const item of movement.items) {
    if (item.quantity <= 0) {
      logger.warn(`Invalid quantity ${item.quantity} for item ${item.collectionId}`);
      continue;
    }

    if (item.collection === "products") {
      const sign = movement.type === "PURCHASE" ? 1 : -1;
      delta[item.collectionId] = (delta[item.collectionId] || 0) + (item.quantity * sign);
      
    } else if (item.collection === "recipes") {
      const recipeBOM = await getRecipeBOM(item.collectionId);
      if (recipeBOM) {
        const sign = movement.type === "PURCHASE" ? 1 : -1;
        for (const bomEntry of recipeBOM.bom) {
          delta[bomEntry.productId] = (delta[bomEntry.productId] || 0) + 
            (bomEntry.quantity * item.quantity * sign);
        }
      } else {
        logger.warn(`Recipe ${item.collectionId} not found or has no BOM`);
      }
    }
  }

  return delta;
}

/**
 * Apply stock changes using transactions
 */
export async function applyStockChanges(movementId: string, delta: { [productId: string]: number }): Promise<void> {
  const batch = db.batch();

  for (const [productId, quantityChange] of Object.entries(delta)) {
    if (quantityChange !== 0) {
      const productRef = db.collection("products").doc(productId);
      batch.update(productRef, {
        quantity: admin.firestore.FieldValue.increment(quantityChange)
      });
    }
  }

  await batch.commit();
  logger.info(`Stock changes applied for movement ${movementId}:`, delta);
}

/**
 * Update product costs from purchase movement
 */
export async function updateProductCostsFromPurchase(movement: Movement): Promise<void> {
  const batch = db.batch();

  for (const item of movement.items) {
    if (item.collection === "products" && item.cost > 0) {
      const productRef = db.collection("products").doc(item.collectionId);
      batch.update(productRef, {
        cost: item.cost
      });
      logger.info(`Updating product ${item.collectionId} cost to ${item.cost}`);
    }
  }

  await batch.commit();
  logger.info("Product costs updated for purchase movement");
}



