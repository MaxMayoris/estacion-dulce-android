import { onDocumentCreated, onDocumentDeleted } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions/v2";
import * as admin from "firebase-admin";
import { Movement } from "../types";
import { calculateMovementDelta, applyStockChanges, updateProductCostsFromPurchase } from "../services/stockService";

const db = admin.firestore();

/**
 * Cloud Function that handles movement creation and stock updates
 */
export const onMovementCreated = onDocumentCreated(
  {
    document: "movements/{movementId}",
    region: "southamerica-east1",
    timeoutSeconds: 60
  },
  async (event) => {
    const movement = event.data?.data() as Movement;
    const movementId = event.params.movementId;

    try {
      if (!movement.type || !movement.items || movement.items.length === 0) {
        logger.error(`Invalid movement ${movementId}: missing type or items`);
        return;
      }

      if (movement.delta && movement.appliedAt) {
        return;
      }

      const delta = await calculateMovementDelta(movement);
      
      if (Object.keys(delta).length === 0) {
        return;
      }

      await applyStockChanges(movementId, delta);

      if (movement.type === "PURCHASE") {
        await updateProductCostsFromPurchase(movement);
      }

      await db.collection("movements").doc(movementId).update({
        delta: delta,
        appliedAt: admin.firestore.FieldValue.serverTimestamp(),
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      });

      const itemCount = Object.keys(delta).length;
      logger.info(`Movement ${movementId} (${movement.type}): ${itemCount} products affected, delta applied`);

    } catch (error) {
      logger.error(`Error processing movement ${movementId}:`, error);
      throw error;
    }
  }
);

/**
 * Cloud Function that handles movement deletion and stock reversal
 */
export const onMovementDeleted = onDocumentDeleted(
  {
    document: "movements/{movementId}",
    region: "southamerica-east1",
    timeoutSeconds: 60
  },
  async (event) => {
    const movement = event.data?.data() as Movement;
    const movementId = event.params.movementId;

    try {
      if (!movement.delta || !movement.appliedAt) {
        return;
      }

      const reverseDelta = Object.fromEntries(
        Object.entries(movement.delta).map(([productId, quantity]) => [productId, -quantity])
      );

      await applyStockChanges(movementId, reverseDelta);
      
      const itemCount = Object.keys(reverseDelta).length;
      logger.info(`Movement ${movementId} deleted: ${itemCount} products reversed`);

    } catch (error) {
      logger.error(`Error reversing movement ${movementId}:`, error);
      throw error;
    }
  }
);



