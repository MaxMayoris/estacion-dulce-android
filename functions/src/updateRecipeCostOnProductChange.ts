import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { calculateRecipeCost } from "./calculateRecipeCost";

admin.initializeApp();

/**
 * Triggered when a product document in the "products" collection is updated.
 * If the product cost has changed, this function queries for recipes that use the
 * product, recalculates their cost (including nested recipes), and updates the
 * recipe documents.
 */
export const updateRecipeCostOnProductChange = functions.firestore
  .onDocumentUpdated("products/{productId}", async (event) => {
    // Guard: ensure event.data exists.
    if (!event.data) {
      return null;
    }

    const productId = event.params.productId;
    const beforeData = event.data.before.data()!;
    const afterData = event.data.after.data()!;

    // Exit early if the cost did not change.
    if (beforeData.cost === afterData.cost) {
      return null;
    }

    // Query recipes that use this product.
    // It is recommended that each recipe document has an array field "productIds"
    // containing the product IDs.
    const recipesSnapshot = await admin.firestore()
      .collection("recipes")
      .where("productIds", "array-contains", productId)
      .get();

    const batch = admin.firestore().batch();

    for (const doc of recipesSnapshot.docs) {
      const recipeData = doc.data();
      // Calculate the new cost using the modular function.
      const newCost = await calculateRecipeCost(recipeData);
      batch.update(doc.ref, { cost: newCost });
    }

    return batch.commit();
  });
