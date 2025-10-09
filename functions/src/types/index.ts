import * as admin from "firebase-admin";

export interface Product {
  id: string;
  name: string;
  quantity: number;
  minimumQuantity: number;
  cost: number;
  measure: string;
}

export interface RecipeProduct {
  productId: string;
  quantity: number;
}

export interface RecipeNested {
  recipeId: string;
  quantity: number;
}

export interface MovementItem {
  collection: string;
  collectionId: string;
  customName?: string;
  cost: number;
  quantity: number;
}

export interface Movement {
  id: string;
  type: "PURCHASE" | "SALE";
  personId: string;
  date: admin.firestore.Timestamp;
  totalAmount: number;
  items: MovementItem[];
  shipment?: any;
  delta?: { [productId: string]: number };
  appliedAt?: admin.firestore.Timestamp;
  createdAt?: admin.firestore.Timestamp;
}

export interface BOMEntry {
  productId: string;
  quantity: number;
}

export interface RecipeBOM {
  productIds: string[];
  recipeIds: string[];
  bom: BOMEntry[];
}

export interface RecipeSection {
  id: string;
  name: string;
  products: RecipeProduct[];
}

export interface Recipe {
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

export interface UpdateResult {
  recipeId: string;
  oldCost: number;
  newCost: number;
  updated: boolean;
  reason: string;
}



