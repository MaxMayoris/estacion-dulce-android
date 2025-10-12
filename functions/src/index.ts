import * as admin from "firebase-admin";

admin.initializeApp();

export { onProductCostUpdate } from "./triggers/productCostTriggers";
export { onRecipeCostUpdate } from "./triggers/recipeCostTriggers";
export { onMovementCreated, onMovementDeleted } from "./triggers/movementTriggers";
export { aiGatewayMCP } from "./triggers/aiGatewayMCPFunction";
