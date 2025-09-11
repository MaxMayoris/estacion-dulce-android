import { onDocumentUpdated, onDocumentCreated, onDocumentDeleted } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions/v2";
import * as admin from "firebase-admin";

// Global metadata store to track update origins
const updateMetadata = new Map<string, string>();

// Global counter to track recursion depth and prevent infinite loops
const recursionCounter = new Map<string, number>();
const MAX_RECURSION_DEPTH = 10;

admin.initializeApp();
const db = admin.firestore();
interface Product {
  id: string;
  name: string;
  quantity: number;
  minimumQuantity: number;
  cost: number;
  measure: string;
}

interface RecipeProduct {
  productId: string;
  quantity: number;
}

interface RecipeNested {
  recipeId: string;
  quantity: number;
}

interface MovementItem {
  collection: string;
  collectionId: string;
  customName?: string;
  cost: number;
  quantity: number;
}

interface Movement {
  id: string;
  type: "PURCHASE" | "SALE";
  personId: string;
  date: admin.firestore.Timestamp;
  totalAmount: number;
  items: MovementItem[];
  shipment?: any;
  // New fields for stock management
  delta?: { [productId: string]: number };
  appliedAt?: admin.firestore.Timestamp;
  createdAt?: admin.firestore.Timestamp;
}

interface BOMEntry {
  productId: string;
  quantity: number;
}

interface RecipeBOM {
  productIds: string[];
  recipeIds: string[];
  bom: BOMEntry[];
}

interface RecipeSection {
  id: string;
  name: string;
  products: RecipeProduct[];
}

interface Recipe {
  id: string;
  name: string;
  cost: number;
  onSale: boolean;
  onSaleQuery: boolean;
  salePrice: number;
  suggestedPrice: number;
  profitPercentage: number;
  unit: number;
  images: string[];
  description: string;
  categories: string[];
  sections: RecipeSection[];
  recipes: RecipeNested[];
}

/**
 * Cloud Function Gen 2 that executes when a product is updated
 * Only runs if the 'cost' field changes
 */
export const onProductCostUpdate = onDocumentUpdated(
  {
    document: "products/{productId}",
    region: "southamerica-east1",
    timeoutSeconds: 300 // 5 minutos máximo
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

/**
 * Cloud Function Gen 2 that executes when a recipe is updated
 * Only runs if the 'cost' field changes and triggers cascade updates
 */
export const onRecipeCostUpdate = onDocumentUpdated(
  {
    document: "recipes/{recipeId}",
    region: "southamerica-east1",
    timeoutSeconds: 300 // 5 minutos máximo
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

    // Check recursion depth to prevent infinite loops
    const currentDepth = recursionCounter.get(recipeId) || 0;
    if (currentDepth >= MAX_RECURSION_DEPTH) {
      logger.error(
        `MAXIMUM RECURSION DEPTH REACHED for recipe ${recipeId} (${currentDepth}). Stopping to prevent infinite loop.`
      );
      recursionCounter.delete(recipeId);
      return;
    }

    // Check if this update was triggered by a product change using metadata
    const updateOrigin = updateMetadata.get(recipeId);
    if (updateOrigin === "product-update") {
      logger.info(
        `Recipe ${recipeId} was updated by product change. Proceeding with cascade update.`
      );
      // Clear the metadata after checking
      updateMetadata.delete(recipeId);
    } else if (updateOrigin === "recipe-cascade") {
      logger.info(
        `Recipe ${recipeId} was updated by recipe cascade. Skipping to prevent circular updates.`
      );
      // Clear the metadata after checking
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

      // Check for circular dependencies
      if (parentRecipes.includes(recipeId)) {
        logger.warn(
          `Circular dependency detected: Recipe ${recipeId} contains itself. Skipping cascade update.`
        );
        return;
      }

      logger.info(
        `Found ${parentRecipes.length} parent recipes containing recipe ${recipeId}`
      );

      // Use a more robust approach: only update if the cost difference is significant
      // This prevents cascading updates from triggering unnecessary recalculations
      if (Math.abs(costDifference) < 0.01) {
        logger.info(
          `Cost difference ${costDifference} is too small. Skipping cascade update.`
        );
        return;
      }

      // Filter out the current recipe from parent recipes to prevent self-updates
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

      // Increment recursion counter for this recipe
      recursionCounter.set(recipeId, currentDepth + 1);

      const updateResults = await updateParentRecipes(
        filteredParentRecipes,
        recipeId,
        costDifference
      );

      logCascadeUpdateResults(updateResults, recipeId, costDifference);

      // Clean up recursion counter
      recursionCounter.delete(recipeId);

    } catch (error) {
      logger.error(
        `Error updating parent recipes for recipe ${recipeId}:`,
        error
      );
      // Clean up recursion counter even on error
      recursionCounter.delete(recipeId);
      throw error;
    }
  }
);

/**
 * Find all recipes containing a specific product
 */
async function findRecipesContainingProduct(productId: string): Promise<string[]> {
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
 * Update all affected recipes, handling recursion
 */
async function updateAffectedRecipes(
  recipeIds: string[],
  updatedProductId: string,
  costDifference: number,
  changeTime: any
): Promise<UpdateResult[]> {
  const results: UpdateResult[] = [];
  const visitedRecipes = new Set<string>(); // Prevent infinite loops

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
async function updateRecipeCost(
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

    // Check freshness barrier: if recipe has sub-recipes that depend on this product,
    // and any of them hasn't been updated yet, defer this update
    if (await shouldDeferRecipeUpdate(recipe, updatedProductId, changeTime)) {
      logger.info(
        `Recipe ${recipeId} has sub-recipes that depend on product ${updatedProductId} but haven't been updated yet. Deferring update.`
      );
      return {
        recipeId,
        oldCost,
        newCost: oldCost,
        updated: false,
        reason: "Deferred: sub-recipes not yet updated"
      };
    }

    const newCost = await calculateRecipeCost(recipe, updatedProductId, costDifference);

    // Calculate new profit percentage
    const newProfitPercentage = calculateProfitPercentage(newCost, recipe.salePrice);

    // Mark this update as coming from product change
    updateMetadata.set(recipeId, "product-update");
    
    await db.collection("recipes").doc(recipeId).update({
      cost: newCost,
      profitPercentage: newProfitPercentage
    });

    logger.info(
      `Updated recipe ${recipeId}: cost changed from ${oldCost} to ${newCost}, profitPercentage changed from ${recipe.profitPercentage}% to ${newProfitPercentage}%`
    );

    // No cascade update here - let the recipe trigger handle parent recipes

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
async function calculateRecipeCost(
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

  return recipe.cost + totalCostChange;
}

/**
 * Check if a recipe update should be deferred due to freshness barrier
 */
async function shouldDeferRecipeUpdate(
  recipe: Recipe,
  productId: string,
  changeTime: any
): Promise<boolean> {
  // If recipe has no sub-recipes, no need to defer
  if (recipe.recipes.length === 0) {
    return false;
  }

  // Check if any sub-recipe depends on the changed product
  const dependentSubRecipes = recipe.recipes.filter(nestedRecipe => {
    // Get the sub-recipe document to check if it contains the product
    return hasRecipeContainingProduct(nestedRecipe.recipeId, productId);
  });

  if (dependentSubRecipes.length === 0) {
    return false;
  }

  // Check if any dependent sub-recipe hasn't been updated yet
  for (const nestedRecipe of dependentSubRecipes) {
    const subRecipeDoc = await db.collection("recipes").doc(nestedRecipe.recipeId).get();
    if (subRecipeDoc.exists) {
      const subRecipeUpdateTime = subRecipeDoc.updateTime;
      
      // If sub-recipe was updated before the product change, defer this update
      if (subRecipeUpdateTime && subRecipeUpdateTime < changeTime) {
        logger.info(
          `Sub-recipe ${nestedRecipe.recipeId} was last updated at ${subRecipeUpdateTime}, before product change at ${changeTime}. Deferring parent recipe update.`
        );
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
  
  // Check direct products
  for (const section of recipe.sections) {
    for (const product of section.products) {
      if (product.productId === productId) {
        return true;
      }
    }
  }

  // Check nested recipes recursively
  for (const nestedRecipe of recipe.recipes) {
    if (await hasRecipeContainingProduct(nestedRecipe.recipeId, productId)) {
      return true;
    }
  }

  return false;
}

/**
 * Find recipes containing a specific recipe as ingredient
 */
async function findRecipesContainingRecipe(recipeId: string): Promise<string[]> {
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
 * Update parent recipes that contain the updated recipe
 */
async function updateParentRecipes(
  parentRecipeIds: string[],
  updatedRecipeId: string,
  costDifference: number
): Promise<UpdateResult[]> {
  const results: UpdateResult[] = [];
  const visitedRecipes = new Set<string>(); // Prevent infinite loops
  const processingRecipes = new Set<string>(); // Track currently processing recipes

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
async function updateParentRecipeCost(
  parentRecipeId: string,
  updatedRecipeId: string,
  costDifference: number,
  visitedRecipes: Set<string>,
  processingRecipes: Set<string>
): Promise<UpdateResult> {
  // Check if this recipe is currently being processed (prevents infinite loops)
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

  // Check if this recipe has already been processed in this cascade
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

  // Additional check: if parentRecipeId is the same as updatedRecipeId, skip
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

  // Mark as currently being processed
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

    // Calculate new cost based on the nested recipe cost change
    const newCost = await calculateParentRecipeCost(
      parentRecipe,
      updatedRecipeId,
      costDifference
    );

    // Calculate new profit percentage
    const newProfitPercentage = calculateProfitPercentage(newCost, parentRecipe.salePrice);
    
    // Mark this update as coming from recipe cascade
    updateMetadata.set(parentRecipeId, "recipe-cascade");
    
    await db.collection("recipes").doc(parentRecipeId).update({
      cost: newCost,
      profitPercentage: newProfitPercentage
    });

    logger.info(
      `Updated parent recipe ${parentRecipeId}: cost changed from ${oldCost} to ${newCost}, profitPercentage changed from ${parentRecipe.profitPercentage}% to ${newProfitPercentage}%`
    );

    // Mark as visited after successful update
    visitedRecipes.add(parentRecipeId);

    // Recursively update grandparent recipes
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
    // Always remove from processing set when done
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

  // Find the nested recipe and calculate the cost change
  for (const nestedRecipe of parentRecipe.recipes) {
    if (nestedRecipe.recipeId === updatedRecipeId) {
      totalCostChange += costDifference * nestedRecipe.quantity;
    }
  }

  return parentRecipe.cost + totalCostChange;
}

/**
 * Calculate profit percentage based on cost and sale price
 */
function calculateProfitPercentage(cost: number, salePrice: number): number {
  if (cost <= 0) return 0;
  return ((salePrice - cost) / cost) * 100;
}

/**
 * Log cascade update results
 */
function logCascadeUpdateResults(
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

interface UpdateResult {
  recipeId: string;
  oldCost: number;
  newCost: number;
  updated: boolean;
  reason: string;
}

/**
 * Log update results
 */
function logUpdateResults(
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
      // Validate input
      if (!movement.type || !movement.items || movement.items.length === 0) {
        logger.error(`Invalid movement ${movementId}: missing type or items`);
        return;
      }

      // Check if already processed
      if (movement.delta && movement.appliedAt) {
        logger.info(`Movement ${movementId} already processed. Skipping.`);
        return;
      }

      // Calculate delta
      const delta = await calculateMovementDelta(movement);
      
      if (Object.keys(delta).length === 0) {
        logger.info(`Movement ${movementId} has no stock impact. Skipping.`);
        return;
      }

      // Apply stock changes in transaction
      await applyStockChanges(movementId, delta);

      // Update product costs for purchases
      if (movement.type === "PURCHASE") {
        await updateProductCostsFromPurchase(movement);
      }

      // Update movement with delta and appliedAt
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
      // Check if movement had stock impact
      if (!movement.delta || !movement.appliedAt) {
        logger.info(`Movement ${movementId} had no stock impact. Skipping reversal.`);
        return;
      }

      // Reverse stock changes
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


/**
 * Calculate the stock delta for a movement
 */
async function calculateMovementDelta(movement: Movement): Promise<{ [productId: string]: number }> {
  const delta: { [productId: string]: number } = {};

  for (const item of movement.items) {
    if (item.quantity <= 0) {
      logger.warn(`Invalid quantity ${item.quantity} for item ${item.collectionId}`);
      continue;
    }

    if (item.collection === "products") {
      // Direct product impact
      const sign = movement.type === "PURCHASE" ? 1 : -1;
      delta[item.collectionId] = (delta[item.collectionId] || 0) + (item.quantity * sign);
      
    } else if (item.collection === "recipes") {
      // Recipe impact - expand BOM
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
async function applyStockChanges(movementId: string, delta: { [productId: string]: number }): Promise<void> {
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
async function updateProductCostsFromPurchase(movement: Movement) {
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

/**
 * Get recipe BOM (calculated dynamically from sections and recipes)
 */
async function getRecipeBOM(recipeId: string): Promise<RecipeBOM | null> {
  const recipeDoc = await db.collection("recipes").doc(recipeId).get();
  
  if (!recipeDoc.exists) {
    return null;
  }

  const recipe = recipeDoc.data() as Recipe;
  
  // Calculate BOM dynamically from sections and recipes
  logger.info(`Calculating BOM for recipe ${recipeId}`);
  return await calculateRecipeBOM(recipe);
}

/**
 * Calculate BOM for a recipe recursively
 */
async function calculateRecipeBOM(recipe: Recipe): Promise<RecipeBOM> {
  const productIds = new Set<string>();
  const recipeIds = new Set<string>();
  const bom: BOMEntry[] = [];
  const visitedRecipes = new Set<string>();

  // Add current recipe to visited set to prevent cycles
  visitedRecipes.add(recipe.id);

  // Process direct products
  for (const section of recipe.sections) {
    for (const product of section.products) {
      productIds.add(product.productId);
      bom.push({
        productId: product.productId,
        quantity: product.quantity
      });
    }
  }

  // Process nested recipes recursively
  for (const nestedRecipe of recipe.recipes) {
    if (visitedRecipes.has(nestedRecipe.recipeId)) {
      logger.warn(`Circular dependency detected: Recipe ${recipe.id} -> ${nestedRecipe.recipeId}`);
      continue;
    }

    recipeIds.add(nestedRecipe.recipeId);
    
    const nestedBOM = await getRecipeBOM(nestedRecipe.recipeId);
    if (nestedBOM) {
      // Add nested recipe's product IDs
      nestedBOM.productIds.forEach(id => productIds.add(id));
      
      // Add nested recipe's recipe IDs
      nestedBOM.recipeIds.forEach(id => recipeIds.add(id));
      
      // Add nested BOM entries with quantity multiplication
      for (const bomEntry of nestedBOM.bom) {
        bom.push({
          productId: bomEntry.productId,
          quantity: bomEntry.quantity * nestedRecipe.quantity
        });
      }
    }
  }

  // Consolidate BOM entries (sum quantities for same products)
  const consolidatedBOM: { [productId: string]: number } = {};
  for (const entry of bom) {
    consolidatedBOM[entry.productId] = (consolidatedBOM[entry.productId] || 0) + entry.quantity;
  }

  const finalBOM: BOMEntry[] = Object.entries(consolidatedBOM).map(([productId, quantity]) => ({
    productId,
    quantity: Math.round(quantity * 10000) / 10000 // Round to 4 decimal places
  }));

  return {
    productIds: Array.from(productIds),
    recipeIds: Array.from(recipeIds),
    bom: finalBOM
  };
}
