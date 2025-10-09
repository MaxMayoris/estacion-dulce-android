import * as admin from "firebase-admin";
import { logger } from "firebase-functions/v2";
import { Recipe, RecipeBOM, BOMEntry } from "../types";

const db = admin.firestore();

/**
 * Get recipe BOM (calculated dynamically from sections and recipes)
 */
export async function getRecipeBOM(recipeId: string): Promise<RecipeBOM | null> {
  const recipeDoc = await db.collection("recipes").doc(recipeId).get();
  
  if (!recipeDoc.exists) {
    return null;
  }

  const recipe = recipeDoc.data() as Recipe;
  
  logger.info(`Calculating BOM for recipe ${recipeId}`);
  return await calculateRecipeBOM(recipe);
}

/**
 * Calculate BOM for a recipe recursively
 */
export async function calculateRecipeBOM(recipe: Recipe): Promise<RecipeBOM> {
  const productIds = new Set<string>();
  const recipeIds = new Set<string>();
  const bom: BOMEntry[] = [];
  const visitedRecipes = new Set<string>();

  visitedRecipes.add(recipe.id);

  for (const section of recipe.sections) {
    for (const product of section.products) {
      productIds.add(product.productId);
      bom.push({
        productId: product.productId,
        quantity: product.quantity
      });
    }
  }

  for (const nestedRecipe of recipe.recipes) {
    if (visitedRecipes.has(nestedRecipe.recipeId)) {
      logger.warn(`Circular dependency detected: Recipe ${recipe.id} -> ${nestedRecipe.recipeId}`);
      continue;
    }

    recipeIds.add(nestedRecipe.recipeId);
    
    const nestedBOM = await getRecipeBOM(nestedRecipe.recipeId);
    if (nestedBOM) {
      nestedBOM.productIds.forEach(id => productIds.add(id));
      nestedBOM.recipeIds.forEach(id => recipeIds.add(id));
      
      for (const bomEntry of nestedBOM.bom) {
        bom.push({
          productId: bomEntry.productId,
          quantity: bomEntry.quantity * nestedRecipe.quantity
        });
      }
    }
  }

  const consolidatedBOM: { [productId: string]: number } = {};
  for (const entry of bom) {
    consolidatedBOM[entry.productId] = (consolidatedBOM[entry.productId] || 0) + entry.quantity;
  }

  const finalBOM: BOMEntry[] = Object.entries(consolidatedBOM).map(([productId, quantity]) => ({
    productId,
    quantity: Math.round(quantity * 10000) / 10000
  }));

  return {
    productIds: Array.from(productIds),
    recipeIds: Array.from(recipeIds),
    bom: finalBOM
  };
}



