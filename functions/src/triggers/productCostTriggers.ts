import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions/v2";
import { Product } from "../types";
import { findRecipesContainingProduct, updateAffectedRecipes } from "../services/recipeService";
import { logUpdateResults } from "../utils/logging";

/**
 * Cloud Function Gen 2 that executes when a product is updated
 * Only runs if the 'cost' field changes
 */
export const onProductCostUpdate = onDocumentUpdated(
  {
    document: "products/{productId}",
    region: "southamerica-east1",
    timeoutSeconds: 300
  },
  async (event) => {
    const before = event.data?.before.data() as Product;
    const after = event.data?.after.data() as Product;
    const productId = event.params.productId;

    if (before.cost === after.cost) {
      logger.info(
        `Product ${productId} updated but cost field unchanged. Skipping.`
      );
      return;
    }

    const costDifference = after.cost - before.cost;
    const changeTime = event.data?.after.updateTime;
    logger.info(
      `Product ${productId} cost changed from ${before.cost} to ${after.cost} (difference: ${costDifference}) at ${changeTime}`
    );

    try {
      const affectedRecipes = await findRecipesContainingProduct(productId);
      
      if (affectedRecipes.length === 0) {
        logger.info(
          `No recipes found containing product ${productId}. No updates needed.`
        );
        return;
      }

      logger.info(
        `Found ${affectedRecipes.length} recipes containing product ${productId}`
      );

      const updateResults = await updateAffectedRecipes(
        affectedRecipes,
        productId,
        costDifference,
        changeTime
      );

      logUpdateResults(updateResults, productId, costDifference);

    } catch (error) {
      logger.error(
        `Error updating recipes for product ${productId}:`,
        error
      );
      throw error;
    }
  }
);



