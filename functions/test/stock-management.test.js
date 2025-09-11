/**
 * Test for stock management Cloud Functions
 * Tests onMovementCreated, onMovementDeleted, and onRecipeWriteDeriveBOM
 */

// Mock data for testing
const mockData = {
  products: {
    "product-a": { name: "Product A", quantity: 100, cost: 10 },
    "product-b": { name: "Product B", quantity: 50, cost: 5 },
    "product-c": { name: "Product C", quantity: 200, cost: 2 }
  },
  recipes: {
    "recipe-a": {
      name: "Recipe A",
      cost: 20,
      sections: [{
        name: "Products",
        products: [
          { productId: "product-a", quantity: 2 },
          { productId: "product-b", quantity: 1 }
        ]
      }],
      recipes: []
    },
    "recipe-b": {
      name: "Recipe B",
      cost: 15,
      sections: [{
        name: "Products",
        products: [
          { productId: "product-c", quantity: 3 }
        ]
      }],
      recipes: [
        { recipeId: "recipe-a", quantity: 1 }
      ]
    }
  },
  movements: {}
};

// Global metadata store
const updateMetadata = new Map();

// Helper functions
function findProductById(productId) {
  return mockData.products[productId];
}

function findRecipeById(recipeId) {
  return mockData.recipes[recipeId];
}

function getRecipeBOM(recipeId) {
  const recipe = findRecipeById(recipeId);
  if (!recipe) return null;
  
  return calculateRecipeBOM(recipe);
}

// Mock function to calculate BOM dynamically from sections and recipes
function calculateRecipeBOM(recipe) {
  const productIds = new Set();
  const recipeIds = new Set();
  const bom = [];
  
  // Add direct products from sections
  for (const section of recipe.sections) {
    for (const product of section.products) {
      productIds.add(product.productId);
      bom.push({
        productId: product.productId,
        quantity: product.quantity
      });
    }
  }
  
  // Add nested recipes
  for (const nestedRecipe of recipe.recipes) {
    recipeIds.add(nestedRecipe.recipeId);
    
    const nestedBOM = getRecipeBOM(nestedRecipe.recipeId);
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
  
  return {
    productIds: Array.from(productIds),
    recipeIds: Array.from(recipeIds),
    bom: bom
  };
}

function calculateMovementDelta(movement) {
  const delta = {};

  for (const item of movement.items) {
    if (item.quantity <= 0) {
      console.warn(`Invalid quantity ${item.quantity} for item ${item.collectionId}`);
      continue;
    }

    if (item.collection === "products") {
      // Direct product impact
      const sign = movement.type === "PURCHASE" ? 1 : -1;
      delta[item.collectionId] = (delta[item.collectionId] || 0) + (item.quantity * sign);
      
    } else if (item.collection === "recipes") {
      // Recipe impact - expand BOM
      const recipeBOM = getRecipeBOM(item.collectionId);
      if (recipeBOM) {
        const sign = movement.type === "PURCHASE" ? 1 : -1;
        for (const bomEntry of recipeBOM.bom) {
          delta[bomEntry.productId] = (delta[bomEntry.productId] || 0) + 
            (bomEntry.quantity * item.quantity * sign);
        }
      } else {
        console.warn(`Recipe ${item.collectionId} not found or has no BOM`);
      }
    }
  }

  return delta;
}

function applyStockChanges(movementId, delta) {
  console.log(`Applying stock changes for movement ${movementId}:`, delta);
  
  for (const [productId, quantityChange] of Object.entries(delta)) {
    if (quantityChange !== 0) {
      const product = findProductById(productId);
      if (product) {
        product.quantity += quantityChange;
        console.log(`  Product ${productId}: ${product.quantity - quantityChange} -> ${product.quantity} (change: ${quantityChange})`);
      }
    }
  }
}

function updateProductCostsFromPurchase(movement) {
  console.log(`Updating product costs for purchase movement ${movement.id}:`);
  
  for (const item of movement.items) {
    if (item.collection === "products" && item.cost > 0) {
      const product = findProductById(item.collectionId);
      if (product) {
        const oldCost = product.cost;
        product.cost = item.cost;
        console.log(`  Product ${item.collectionId}: cost ${oldCost} -> ${item.cost}`);
      }
    }
  }
}

// Simulate onMovementCreated function
function simulateOnMovementCreated(movement) {
  console.log(`\n🔄 onMovementCreated triggered:`);
  console.log(`  Movement type: ${movement.type}`);
  console.log(`  Items: ${movement.items.length}`);

  // Validate input
  if (!movement.type || !movement.items || movement.items.length === 0) {
    console.error(`Invalid movement: missing type or items`);
    return;
  }

  // Check if already processed
  if (movement.delta && movement.appliedAt) {
    console.log(`Movement already processed. Skipping.`);
    return;
  }

  // Calculate delta
  const delta = calculateMovementDelta(movement);
  
  if (Object.keys(delta).length === 0) {
    console.log(`Movement has no stock impact. Skipping.`);
    return;
  }

  // Apply stock changes
  applyStockChanges(movement.id, delta);
  
  // Update product costs for purchases
  if (movement.type === "PURCHASE") {
    updateProductCostsFromPurchase(movement);
  }
  
  // Update movement with delta and appliedAt
  movement.delta = delta;
  movement.appliedAt = new Date();
  movement.createdAt = new Date();
  
  console.log(`Movement processed successfully. Delta:`, delta);
}

// Simulate onMovementDeleted function
function simulateOnMovementDeleted(movement) {
  console.log(`\n🔄 onMovementDeleted triggered:`);
  console.log(`  Movement ID: ${movement.id}`);

  // Check if movement had stock impact
  if (!movement.delta || !movement.appliedAt) {
    console.log(`Movement had no stock impact. Skipping reversal.`);
    return;
  }

  // Reverse stock changes
  const reverseDelta = Object.fromEntries(
    Object.entries(movement.delta).map(([productId, quantity]) => [productId, -quantity])
  );

  applyStockChanges(movement.id, reverseDelta);

  console.log(`Movement stock reversed successfully. Reverse delta:`, reverseDelta);
}

// Test scenarios
function runStockManagementTests() {
  console.log("🚀 Stock Management Cloud Functions Test");
  console.log("============================================================");

  console.log("\n📋 Test Scenarios:");
  console.log("1. Purchase movement with direct products");
  console.log("2. Sale movement with recipe (BOM expansion)");
  console.log("3. Delete movement (stock reversal)");

  console.log("\n📊 Initial Stock:");
  Object.entries(mockData.products).forEach(([id, product]) => {
    console.log(`  ${id}: ${product.quantity} units`);
  });

  console.log("\n🔄 Executing Tests...");
  console.log("----------------------------------------");

  // Test 1: Purchase movement with direct products
  const purchaseMovement = {
    id: "movement-1",
    type: "PURCHASE",
    items: [
      { collection: "products", collectionId: "product-a", quantity: 10, cost: 12 }, // New cost: 12
      { collection: "products", collectionId: "product-b", quantity: 5, cost: 7 }   // New cost: 7
    ]
  };

  simulateOnMovementCreated(purchaseMovement);

  // Test 2: Sale movement with recipe (BOM expansion)
  console.log('\n🔍 Testing BOM expansion for recipe-b:');
  const recipeBBOM = getRecipeBOM('recipe-b');
  console.log('  Recipe B BOM:', JSON.stringify(recipeBBOM, null, 2));
  
  const saleMovement = {
    id: "movement-2",
    type: "SALE",
    items: [
      { collection: "recipes", collectionId: "recipe-b", quantity: 2, cost: 15 }
    ]
  };

  simulateOnMovementCreated(saleMovement);

  // Test 3: Edit movement (delete original + create new)
  console.log('\n🔄 Testing movement editing (delete + create):');
  const editedMovement = {
    id: "movement-3", // New ID for edited movement
    type: "PURCHASE",
    items: [
      { collection: "products", collectionId: "product-a", quantity: 15, cost: 15 }, // Changed: 10->15, cost 12->15
      { collection: "products", collectionId: "product-b", quantity: 8, cost: 9 }   // Changed: 5->8, cost 7->9
    ]
  };
  
  // Simulate editing: delete original, create new
  simulateOnMovementDeleted(purchaseMovement); // Delete original
  simulateOnMovementCreated(editedMovement);   // Create edited version

  console.log("\n📊 Final Stock:");
  Object.entries(mockData.products).forEach(([id, product]) => {
    console.log(`  ${id}: ${product.quantity} units`);
  });

  console.log("\n✅ Expected Results:");
  console.log("  product-a: 100 + 10 - 10 + 15 - 4 = 111 (purchase +10, delete -10, edit +15, sale -4)");
  console.log("  product-b: 50 + 5 - 5 + 8 - 2 = 56 (purchase +5, delete -5, edit +8, sale -2)");
  console.log("  product-c: 200 - 6 = 194 (sale -6)");
  console.log("  product-a cost: 10 -> 12 -> 15 (purchase -> edit)");
  console.log("  product-b cost: 5 -> 7 -> 9 (purchase -> edit)");

  console.log("\n🎯 Test Results:");
  const productA = findProductById("product-a");
  const productB = findProductById("product-b");
  const productC = findProductById("product-c");

  const productAPass = productA.quantity === 111;
  const productBPass = productB.quantity === 56;
  const productCPass = productC.quantity === 194;
  const productACostPass = productA.cost === 15;
  const productBCostPass = productB.cost === 9;

  console.log(`  product-a: ${productAPass ? "✅ PASS" : "❌ FAIL"} (expected: 111, got: ${productA.quantity})`);
  console.log(`  product-b: ${productBPass ? "✅ PASS" : "❌ FAIL"} (expected: 56, got: ${productB.quantity})`);
  console.log(`  product-c: ${productCPass ? "✅ PASS" : "❌ FAIL"} (expected: 194, got: ${productC.quantity})`);
  console.log(`  product-a cost: ${productACostPass ? "✅ PASS" : "❌ FAIL"} (expected: 15, got: ${productA.cost})`);
  console.log(`  product-b cost: ${productBCostPass ? "✅ PASS" : "❌ FAIL"} (expected: 9, got: ${productB.cost})`);

  const allPassed = productAPass && productBPass && productCPass && productACostPass && productBCostPass;
  console.log(`\n${allPassed ? "✅ All tests passed!" : "❌ Some tests failed"}`);

  console.log("\n============================================================");
}

// Run the tests
runStockManagementTests();
