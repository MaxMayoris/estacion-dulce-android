# Cloud Functions Tests

This directory contains tests for Firebase Cloud Functions.

## Tests

### `cascade-functions.test.js`

Tests the cascade update logic for both `onProductCostUpdate` and `onRecipeCostUpdate` Cloud Functions.

### `stock-management.test.js`

Tests the stock management logic for `onMovementCreated`, `onMovementDeleted`, and BOM expansion.

### `verify-setup.js`

Verification script that validates Firebase Functions Gen 2 configuration before deployment.

**Features verified:**
- ✅ Required files exist (package.json, tsconfig.json, src/index.ts, firebase.json, .firebaserc)
- ✅ Dependencies are correct (firebase-functions v5.x, firebase-admin)
- ✅ Firebase configuration is valid (runtime nodejs18, source directory)
- ✅ Environment configuration (dev/prod projects)
- ✅ TypeScript code uses Gen 2 imports (no legacy Gen 1 code)

**Features tested:**
- ✅ Product cost changes trigger recipe updates
- ✅ Recipe cost changes trigger parent recipe updates (cascade)
- ✅ Circular dependency prevention
- ✅ Freshness barrier implementation
- ✅ Metadata tracking to prevent infinite loops
- ✅ Recursion depth limits

**Test scenario:**
- Product A: cost 10 → 20 (+10)
- Recipe A: contains Product A (qty: 2) + Recipe B (qty: 1)
- Recipe B: contains Product A (qty: 1) - no sub-recipes
- Expected: Recipe A = 120, Recipe B = 70, NO INFINITE LOOP

**Run tests:**
```bash
# Test de lógica de cascada
npm test

# Test de manejo de stock
npm run test:stock

# Todos los tests
npm run test:all

# Verificación de configuración
npm run verify
```

## Test Results

The test validates that:
1. Recipe B updates first (50 → 60 → 70) because it has no sub-recipes
2. Recipe A is deferred initially due to freshness barrier
3. Recipe A updates through cascade (100 → 110 → 120) when Recipe B changes
4. No infinite loops occur due to metadata tracking
5. All cost calculations are accurate
