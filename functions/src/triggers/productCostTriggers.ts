import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions/v2";
import { Product } from "../types";
import { findRecipesContainingProduct, updateAffectedRecipes } from "../services/recipeService";

/**
 * Cloud Function Gen 2 that executes when a product is updated
 * Only runs if the 'cost' field changes
 */
export const onProductCostUpdate = onDocumentUpdated(
  {
    document: "products/{productId}",
    region: "southamerica-east1",
    timeoutSeconds: 60
  },
  async (event) => {
    const before = event.data?.before.data() as Product;
    const after = event.data?.after.data() as Product;
    const productId = event.params.productId;

    if (before.cost === after.cost) {
      return;
    }

    const costDifference = after.cost - before.cost;
    const changeTime = event.data?.after.updateTime;

    try {
      const affectedRecipes = await findRecipesContainingProduct(productId);
      
      if (affectedRecipes.length === 0) {
        return;
      }

      await updateAffectedRecipes(
        affectedRecipes,
        productId,
        costDifference,
        changeTime
      );

      logger.info(`Product ${productId}: cost ${before.cost} → ${after.cost} (Δ${costDifference}), ${affectedRecipes.length} recipes updated`);

    } catch (error) {
      logger.error(`Error updating recipes for product ${productId}:`, error);
      throw error;
    }
  }
);



