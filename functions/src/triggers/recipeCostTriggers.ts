import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions/v2";
import { Recipe } from "../types";
import { findRecipesContainingRecipe, updateParentRecipes } from "../services/recipeService";
import { updateMetadata, recursionCounter, MAX_RECURSION_DEPTH } from "../utils/metadata";

/**
 * Cloud Function Gen 2 that executes when a recipe is updated
 * Only runs if the 'cost' field changes and triggers cascade updates
 */
export const onRecipeCostUpdate = onDocumentUpdated(
  {
    document: "recipes/{recipeId}",
    region: "southamerica-east1",
    timeoutSeconds: 60
  },
  async (event) => {
    const before = event.data?.before.data() as Recipe;
    const after = event.data?.after.data() as Recipe;
    const recipeId = event.params.recipeId;

    if (before.cost === after.cost) {
      return;
    }

    const currentDepth = recursionCounter.get(recipeId) || 0;
    if (currentDepth >= MAX_RECURSION_DEPTH) {
      logger.error(`Max recursion depth reached for recipe ${recipeId}`);
      recursionCounter.delete(recipeId);
      return;
    }

    const updateOrigin = updateMetadata.get(recipeId);
    if (updateOrigin === "product-update") {
      updateMetadata.delete(recipeId);
    } else if (updateOrigin === "recipe-cascade") {
      updateMetadata.delete(recipeId);
      return;
    }

    const costDifference = after.cost - before.cost;

    try {
      const parentRecipes = await findRecipesContainingRecipe(recipeId);

      if (parentRecipes.length === 0) {
        return;
      }

      if (parentRecipes.includes(recipeId)) {
        return;
      }

      if (Math.abs(costDifference) < 0.01) {
        return;
      }

      const filteredParentRecipes = parentRecipes.filter(id => id !== recipeId);
      
      if (filteredParentRecipes.length === 0) {
        return;
      }

      recursionCounter.set(recipeId, currentDepth + 1);

      await updateParentRecipes(
        filteredParentRecipes,
        recipeId,
        costDifference
      );

      recursionCounter.delete(recipeId);
      
      logger.info(`Recipe ${recipeId}: cost ${before.cost} → ${after.cost} (Δ${costDifference}), ${filteredParentRecipes.length} parents updated`);

    } catch (error) {
      logger.error(`Error updating parent recipes for recipe ${recipeId}:`, error);
      recursionCounter.delete(recipeId);
      throw error;
    }
  }
);



