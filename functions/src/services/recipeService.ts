import * as admin from "firebase-admin";
import { logger } from "firebase-functions/v2";
import { Recipe, UpdateResult } from "../types";
import { updateMetadata } from "../utils/metadata";
import { calculateProfitPercentage } from "../utils/calculations";

const db = admin.firestore();

/**
 * Find all recipes containing a specific product
 */
export async function findRecipesContainingProduct(productId: string): Promise<string[]> {
  const recipesSnapshot = await db.collection("recipes").get();
  const affectedRecipeIds: string[] = [];

  for (const doc of recipesSnapshot.docs) {
    const recipe = doc.data() as Recipe;
    
    const containsProduct = recipe.sections.some(section =>
      section.products.some(product => product.productId === productId)
    );

    if (containsProduct) {
      affectedRecipeIds.push(doc.id);
    }
  }

  return affectedRecipeIds;
}

/**
 * Find recipes containing a specific recipe as ingredient
 */
export async function findRecipesContainingRecipe(recipeId: string): Promise<string[]> {
  const recipesSnapshot = await db.collection("recipes").get();
  const dependentRecipeIds: string[] = [];

  for (const doc of recipesSnapshot.docs) {
    const recipe = doc.data() as Recipe;
    
    const containsRecipe = recipe.recipes.some(nestedRecipe => 
      nestedRecipe.recipeId === recipeId
    );

    if (containsRecipe) {
      dependentRecipeIds.push(doc.id);
    }
  }

  return dependentRecipeIds;
}

/**
 * Update all affected recipes, handling recursion
 */
export async function updateAffectedRecipes(
  recipeIds: string[],
  updatedProductId: string,
  costDifference: number,
  changeTime: any
): Promise<UpdateResult[]> {
  const results: UpdateResult[] = [];
  const visitedRecipes = new Set<string>();

  for (const recipeId of recipeIds) {
    const result = await updateRecipeCost(
      recipeId,
      updatedProductId,
      costDifference,
      visitedRecipes,
      changeTime
    );
    results.push(result);
  }

  return results;
}

/**
 * Update the cost of a specific recipe
 */
export async function updateRecipeCost(
  recipeId: string,
  updatedProductId: string,
  costDifference: number,
  visitedRecipes: Set<string>,
  changeTime: any
): Promise<UpdateResult> {
  if (visitedRecipes.has(recipeId)) {
    logger.warn(
      `Circular dependency detected for recipe ${recipeId}. Skipping to avoid infinite loop.`
    );
    return {
      recipeId,
      oldCost: 0,
      newCost: 0,
      updated: false,
      reason: "Circular dependency detected"
    };
  }

  visitedRecipes.add(recipeId);

  try {
    const recipeDoc = await db.collection("recipes").doc(recipeId).get();
    
    if (!recipeDoc.exists) {
      logger.warn(`Recipe ${recipeId} not found`);
      return {
        recipeId,
        oldCost: 0,
        newCost: 0,
        updated: false,
        reason: "Recipe not found"
      };
    }

    const recipe = recipeDoc.data() as Recipe;
    const oldCost = recipe.cost;

    if (await shouldDeferRecipeUpdate(recipe, updatedProductId, changeTime)) {
      return {
        recipeId,
        oldCost,
        newCost: oldCost,
        updated: false,
        reason: "Deferred: sub-recipes not yet updated"
      };
    }

    const newCost = await calculateRecipeCost(recipe, updatedProductId, costDifference);
    const newProfitPercentage = calculateProfitPercentage(newCost, recipe.salePrice);

    updateMetadata.set(recipeId, "product-update");
    
    await db.collection("recipes").doc(recipeId).update({
      cost: newCost,
      profitPercentage: newProfitPercentage
    });

    return {
      recipeId,
      oldCost,
      newCost,
      updated: true,
      reason: "Successfully updated"
    };

  } catch (error) {
    logger.error(`Error updating recipe ${recipeId}:`, error);
    return {
      recipeId,
      oldCost: 0,
      newCost: 0,
      updated: false,
      reason: `Error: ${error}`
    };
  } finally {
    visitedRecipes.delete(recipeId);
  }
}

/**
 * Calculate new recipe cost based on product cost change
 */
export async function calculateRecipeCost(
  recipe: Recipe,
  updatedProductId: string,
  costDifference: number
): Promise<number> {
  let totalCostChange = 0;

  for (const section of recipe.sections) {
    for (const product of section.products) {
      if (product.productId === updatedProductId) {
        totalCostChange += product.quantity * costDifference;
      }
    }
  }

  for (const nestedRecipe of recipe.recipes) {
    const nestedRecipeDoc = await db.collection("recipes").doc(nestedRecipe.recipeId).get();
    if (nestedRecipeDoc.exists) {
      const nestedRecipeData = nestedRecipeDoc.data() as Recipe;
      const nestedRecipeNewCost = await calculateRecipeCost(
        nestedRecipeData,
        updatedProductId,
        costDifference
      );
      const nestedRecipeOldCost = nestedRecipeData.cost;
      const nestedCostChange = (nestedRecipeNewCost - nestedRecipeOldCost) * nestedRecipe.quantity;
      totalCostChange += nestedCostChange;
    }
  }

  const units = recipe.unit > 0 ? recipe.unit : 1;
  const costChangePerUnit = totalCostChange / units;
  
  return recipe.cost + costChangePerUnit;
}

/**
 * Check if a recipe update should be deferred due to freshness barrier
 */
async function shouldDeferRecipeUpdate(
  recipe: Recipe,
  productId: string,
  changeTime: any
): Promise<boolean> {
  if (recipe.recipes.length === 0) {
    return false;
  }

  const dependentSubRecipes = recipe.recipes.filter(nestedRecipe => {
    return hasRecipeContainingProduct(nestedRecipe.recipeId, productId);
  });

  if (dependentSubRecipes.length === 0) {
    return false;
  }

  for (const nestedRecipe of dependentSubRecipes) {
    const subRecipeDoc = await db.collection("recipes").doc(nestedRecipe.recipeId).get();
    if (subRecipeDoc.exists) {
      const subRecipeUpdateTime = subRecipeDoc.updateTime;
      
      if (subRecipeUpdateTime && subRecipeUpdateTime < changeTime) {
        return true;
      }
    }
  }

  return false;
}

/**
 * Check if a recipe contains a specific product (recursive)
 */
async function hasRecipeContainingProduct(recipeId: string, productId: string): Promise<boolean> {
  const recipeDoc = await db.collection("recipes").doc(recipeId).get();
  if (!recipeDoc.exists) {
    return false;
  }

  const recipe = recipeDoc.data() as Recipe;
  
  for (const section of recipe.sections) {
    for (const product of section.products) {
      if (product.productId === productId) {
        return true;
      }
    }
  }

  for (const nestedRecipe of recipe.recipes) {
    if (await hasRecipeContainingProduct(nestedRecipe.recipeId, productId)) {
      return true;
    }
  }

  return false;
}

/**
 * Update parent recipes that contain the updated recipe
 */
export async function updateParentRecipes(
  parentRecipeIds: string[],
  updatedRecipeId: string,
  costDifference: number
): Promise<UpdateResult[]> {
  const results: UpdateResult[] = [];
  const visitedRecipes = new Set<string>();
  const processingRecipes = new Set<string>();

  for (const parentRecipeId of parentRecipeIds) {
    const result = await updateParentRecipeCost(
      parentRecipeId,
      updatedRecipeId,
      costDifference,
      visitedRecipes,
      processingRecipes
    );
    results.push(result);
  }

  return results;
}

/**
 * Update the cost of a parent recipe based on nested recipe cost change
 */
export async function updateParentRecipeCost(
  parentRecipeId: string,
  updatedRecipeId: string,
  costDifference: number,
  visitedRecipes: Set<string>,
  processingRecipes: Set<string>
): Promise<UpdateResult> {
  if (processingRecipes.has(parentRecipeId)) {
    logger.warn(
      `Circular dependency detected: Recipe ${parentRecipeId} is currently being processed. Skipping to avoid infinite loop.`
    );
    return {
      recipeId: parentRecipeId,
      oldCost: 0,
      newCost: 0,
      updated: false,
      reason: "Circular dependency: currently being processed"
    };
  }

  if (visitedRecipes.has(parentRecipeId)) {
    logger.info(
      `Recipe ${parentRecipeId} already processed in this cascade. Skipping to avoid duplicate updates.`
    );
    return {
      recipeId: parentRecipeId,
      oldCost: 0,
      newCost: 0,
      updated: false,
      reason: "Already processed in this cascade"
    };
  }

  if (parentRecipeId === updatedRecipeId) {
    logger.warn(
      `Circular dependency detected: Parent recipe ${parentRecipeId} is the same as updated recipe ${updatedRecipeId}. Skipping.`
    );
    return {
      recipeId: parentRecipeId,
      oldCost: 0,
      newCost: 0,
      updated: false,
      reason: "Circular dependency: parent equals updated recipe"
    };
  }

  processingRecipes.add(parentRecipeId);

  try {
    const parentRecipeDoc = await db.collection("recipes").doc(parentRecipeId).get();
    
    if (!parentRecipeDoc.exists) {
      logger.warn(`Parent recipe ${parentRecipeId} not found`);
      return {
        recipeId: parentRecipeId,
        oldCost: 0,
        newCost: 0,
        updated: false,
        reason: "Parent recipe not found"
      };
    }

    const parentRecipe = parentRecipeDoc.data() as Recipe;
    const oldCost = parentRecipe.cost;

    const newCost = await calculateParentRecipeCost(
      parentRecipe,
      updatedRecipeId,
      costDifference
    );

    const newProfitPercentage = calculateProfitPercentage(newCost, parentRecipe.salePrice);
    
    updateMetadata.set(parentRecipeId, "recipe-cascade");
    
    await db.collection("recipes").doc(parentRecipeId).update({
      cost: newCost,
      profitPercentage: newProfitPercentage
    });

    visitedRecipes.add(parentRecipeId);

    const grandparentRecipes = await findRecipesContainingRecipe(parentRecipeId);
    const filteredGrandparentRecipes = grandparentRecipes.filter(id => id !== parentRecipeId);
    
    for (const grandparentRecipeId of filteredGrandparentRecipes) {
      if (!visitedRecipes.has(grandparentRecipeId) && !processingRecipes.has(grandparentRecipeId)) {
        await updateParentRecipeCost(
          grandparentRecipeId,
          parentRecipeId,
          newCost - oldCost,
          visitedRecipes,
          processingRecipes
        );
      }
    }

    return {
      recipeId: parentRecipeId,
      oldCost,
      newCost,
      updated: true,
      reason: "Successfully updated"
    };

  } catch (error) {
    logger.error(`Error updating parent recipe ${parentRecipeId}:`, error);
    return {
      recipeId: parentRecipeId,
      oldCost: 0,
      newCost: 0,
      updated: false,
      reason: `Error: ${error}`
    };
  } finally {
    processingRecipes.delete(parentRecipeId);
  }
}

/**
 * Calculate new cost for parent recipe based on nested recipe cost change
 */
async function calculateParentRecipeCost(
  parentRecipe: Recipe,
  updatedRecipeId: string,
  costDifference: number
): Promise<number> {
  let totalCostChange = 0;

  for (const nestedRecipe of parentRecipe.recipes) {
    if (nestedRecipe.recipeId === updatedRecipeId) {
      totalCostChange += costDifference * nestedRecipe.quantity;
    }
  }

  const units = parentRecipe.unit > 0 ? parentRecipe.unit : 1;
  const costChangePerUnit = totalCostChange / units;
  
  return parentRecipe.cost + costChangePerUnit;
}


