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
    timeoutSeconds: 300
  },
  async (event) => {
    const movement = event.data?.data() as Movement;
    const movementId = event.params.movementId;

    logger.info(`Movement ${movementId} created with type: ${movement.type}`);

    try {
      if (!movement.type || !movement.items || movement.items.length === 0) {
        logger.error(`Invalid movement ${movementId}: missing type or items`);
        return;
      }

      if (movement.delta && movement.appliedAt) {
        logger.info(`Movement ${movementId} already processed. Skipping.`);
        return;
      }

      const delta = await calculateMovementDelta(movement);
      
      if (Object.keys(delta).length === 0) {
        logger.info(`Movement ${movementId} has no stock impact. Skipping.`);
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

      logger.info(`Movement ${movementId} processed successfully. Delta:`, delta);

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
    timeoutSeconds: 300
  },
  async (event) => {
    const movement = event.data?.data() as Movement;
    const movementId = event.params.movementId;

    logger.info(`Movement ${movementId} deleted`);

    try {
      if (!movement.delta || !movement.appliedAt) {
        logger.info(`Movement ${movementId} had no stock impact. Skipping reversal.`);
        return;
      }

      const reverseDelta = Object.fromEntries(
        Object.entries(movement.delta).map(([productId, quantity]) => [productId, -quantity])
      );

      await applyStockChanges(movementId, reverseDelta);

      logger.info(`Movement ${movementId} stock reversed successfully. Reverse delta:`, reverseDelta);
      logger.info("Note: Movement document was deleted, so reversedAt cannot be recorded.");

    } catch (error) {
      logger.error(`Error reversing movement ${movementId}:`, error);
      throw error;
    }
  }
);



