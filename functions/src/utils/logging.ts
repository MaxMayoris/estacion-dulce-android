import { logger } from "firebase-functions/v2";
import { UpdateResult } from "../types";

/**
 * Log update results
 */
export function logUpdateResults(
  results: UpdateResult[],
  productId: string,
  costDifference: number
): void {
  const successfulUpdates = results.filter(r => r.updated);
  const failedUpdates = results.filter(r => !r.updated);

  logger.info(
    `=== UPDATE SUMMARY for Product ${productId} (cost change: ${costDifference}) ===`
  );

  if (successfulUpdates.length > 0) {
    logger.info(`✅ Successfully updated ${successfulUpdates.length} recipes:`);
    successfulUpdates.forEach(result => {
      logger.info(
        `  - Recipe ${result.recipeId}: ${result.oldCost} → ${result.newCost}`
      );
    });
  }

  if (failedUpdates.length > 0) {
    logger.warn(`❌ Failed to update ${failedUpdates.length} recipes:`);
    failedUpdates.forEach(result => {
      logger.warn(
        `  - Recipe ${result.recipeId}: ${result.reason}`
      );
    });
  }

  logger.info("=== END UPDATE SUMMARY ===");
}

/**
 * Log cascade update results
 */
export function logCascadeUpdateResults(
  results: UpdateResult[],
  updatedRecipeId: string,
  costDifference: number
): void {
  const successfulUpdates = results.filter(r => r.updated);
  const failedUpdates = results.filter(r => !r.updated);

  logger.info(
    `=== CASCADE UPDATE SUMMARY for Recipe ${updatedRecipeId} (cost change: ${costDifference}) ===`
  );

  if (successfulUpdates.length > 0) {
    logger.info(`✅ Successfully updated ${successfulUpdates.length} parent recipes:`);
    successfulUpdates.forEach(result => {
      logger.info(
        `  - Parent Recipe ${result.recipeId}: ${result.oldCost} → ${result.newCost}`
      );
    });
  }

  if (failedUpdates.length > 0) {
    logger.warn(`❌ Failed to update ${failedUpdates.length} parent recipes:`);
    failedUpdates.forEach(result => {
      logger.warn(
        `  - Parent Recipe ${result.recipeId}: ${result.reason}`
      );
    });
  }

  logger.info("=== END CASCADE UPDATE SUMMARY ===");
}



