import { onDocumentCreated, onDocumentDeleted, onDocumentUpdated } from "firebase-functions/v2/firestore";
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
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
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
 * Reverts the stock applied by a movement. Shared by soft and hard deletion.
 */
async function revertMovementStock(movementId: string, movement: Movement): Promise<void> {
  if (!movement.delta || !movement.appliedAt) {
    return;
  }

  const reverseDelta = Object.fromEntries(
    Object.entries(movement.delta).map(([productId, quantity]) => [productId, -quantity])
  );

  await applyStockChanges(movementId, reverseDelta);

  const itemCount = Object.keys(reverseDelta).length;
  logger.info(`Movement ${movementId} deleted: ${itemCount} products reversed`);
}

/**
 * Cloud Function that reverts stock when a movement is soft-deleted (`deleted: true`).
 * The app soft-deletes so the deletion reaches devices that sync movements incrementally.
 */
export const onMovementUpdated = onDocumentUpdated(
  {
    document: "movements/{movementId}",
    region: "southamerica-east1",
    timeoutSeconds: 60
  },
  async (event) => {
    const before = event.data?.before.data() as Movement | undefined;
    const after = event.data?.after.data() as Movement | undefined;
    const movementId = event.params.movementId;

    if (!before || !after || before.deleted === true || after.deleted !== true) {
      return;
    }

    try {
      await revertMovementStock(movementId, after);
    } catch (error) {
      logger.error(`Error reversing soft-deleted movement ${movementId}:`, error);
      throw error;
    }
  }
);

/**
 * Cloud Function that handles hard deletion (e.g. from the console) and stock reversal.
 * Skips movements already soft-deleted, whose stock was reverted by onMovementUpdated.
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
      if (!movement || movement.deleted === true) {
        return;
      }

      await revertMovementStock(movementId, movement);
    } catch (error) {
      logger.error(`Error reversing movement ${movementId}:`, error);
      throw error;
    }
  }
);



