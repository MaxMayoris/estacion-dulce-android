import * as admin from "firebase-admin";

/**
 * Calculates the total cost of a recipe.
 *
 * @param {FirebaseFirestore.DocumentData} recipeData - The recipe document data.
 * @return {Promise<number>} The calculated total cost.
 */
export async function calculateRecipeCost(
  recipeData: FirebaseFirestore.DocumentData
): Promise<number> {
  let totalCost = 0;

  // Calculate cost from sections (each section contains products)
  if (Array.isArray(recipeData.sections)) {
    for (const section of recipeData.sections) {
      if (Array.isArray(section.products)) {
        for (const recipeProduct of section.products) {
          let productCost: number;
          // Option 1: Use the product cost if already stored in the recipeProduct.
          if (recipeProduct.cost !== undefined) {
            productCost = recipeProduct.cost;
          } else {
            // Otherwise, fetch the product cost from the "products" collection.
            const productDoc = await admin
              .firestore()
              .collection("products")
              .doc(recipeProduct.productId)
              .get();
            productCost = productDoc.exists? (productDoc.data()?.cost || 0) : 0;
          }
          totalCost += productCost * (recipeProduct.quantity || 0);
        }
      }
    }
  }

  // Calculate cost from nested recipes.
  if (Array.isArray(recipeData.recipes)) {
    for (const nested of recipeData.recipes) {
      // Each nested recipe has a recipeId and a quantity.
      const nestedDoc = await admin
        .firestore()
        .collection("recipes")
        .doc(nested.recipeId)
        .get();
      if (nestedDoc.exists) {
        const nestedData = nestedDoc.data();
        // Assume the nested recipe document already stores a 'cost' field.
        // (Alternatively, you can call calculateRecipeCost recursively.)
        const nestedCost = nestedData?.cost || 0;
        totalCost += nestedCost * (nested.quantity || 0);
      }
    }
  }

  return totalCost;
}
