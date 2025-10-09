import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions/v2";
import { Recipe } from "../types";
import { findRecipesContainingRecipe, updateParentRecipes } from "../services/recipeService";
import { logCascadeUpdateResults } from "../utils/logging";
import { updateMetadata, recursionCounter, MAX_RECURSION_DEPTH } from "../utils/metadata";

/**
 * Cloud Function Gen 2 that executes when a recipe is updated
 * Only runs if the 'cost' field changes and triggers cascade updates
 */
export const onRecipeCostUpdate = onDocumentUpdated(
  {
    document: "recipes/{recipeId}",
    region: "southamerica-east1",
    timeoutSeconds: 300
  },
  async (event) => {
    const before = event.data?.before.data() as Recipe;
    const after = event.data?.after.data() as Recipe;
    const recipeId = event.params.recipeId;

    if (before.cost === after.cost) {
      logger.info(
        `Recipe ${recipeId} updated but cost field unchanged. Skipping.`
      );
      return;
    }

    const currentDepth = recursionCounter.get(recipeId) || 0;
    if (currentDepth >= MAX_RECURSION_DEPTH) {
      logger.error(
        `MAXIMUM RECURSION DEPTH REACHED for recipe ${recipeId} (${currentDepth}). Stopping to prevent infinite loop.`
      );
      recursionCounter.delete(recipeId);
      return;
    }

    const updateOrigin = updateMetadata.get(recipeId);
    if (updateOrigin === "product-update") {
      logger.info(
        `Recipe ${recipeId} was updated by product change. Proceeding with cascade update.`
      );
      updateMetadata.delete(recipeId);
    } else if (updateOrigin === "recipe-cascade") {
      logger.info(
        `Recipe ${recipeId} was updated by recipe cascade. Skipping to prevent circular updates.`
      );
      updateMetadata.delete(recipeId);
      return;
    }

    const costDifference = after.cost - before.cost;
    logger.info(
      `Recipe ${recipeId} cost changed from ${before.cost} to ${after.cost} (difference: ${costDifference})`
    );

    try {
      const parentRecipes = await findRecipesContainingRecipe(recipeId);

      if (parentRecipes.length === 0) {
        logger.info(
          `No parent recipes found containing recipe ${recipeId}. No cascade updates needed.`
        );
        return;
      }

      if (parentRecipes.includes(recipeId)) {
        logger.warn(
          `Circular dependency detected: Recipe ${recipeId} contains itself. Skipping cascade update.`
        );
        return;
      }

      logger.info(
        `Found ${parentRecipes.length} parent recipes containing recipe ${recipeId}`
      );

      if (Math.abs(costDifference) < 0.01) {
        logger.info(
          `Cost difference ${costDifference} is too small. Skipping cascade update.`
        );
        return;
      }

      const filteredParentRecipes = parentRecipes.filter(id => id !== recipeId);
      
      if (filteredParentRecipes.length === 0) {
        logger.info(
          "No valid parent recipes found (filtered out self-references). Skipping cascade update."
        );
        return;
      }
      
      logger.info(
        `Found ${filteredParentRecipes.length} valid parent recipes (filtered out self-references)`
      );

      recursionCounter.set(recipeId, currentDepth + 1);

      const updateResults = await updateParentRecipes(
        filteredParentRecipes,
        recipeId,
        costDifference
      );

      logCascadeUpdateResults(updateResults, recipeId, costDifference);

      recursionCounter.delete(recipeId);

    } catch (error) {
      logger.error(
        `Error updating parent recipes for recipe ${recipeId}:`,
        error
      );
      recursionCounter.delete(recipeId);
      throw error;
    }
  }
);



