export interface RecipeProduct {
  productId: string;
  quantity: number;
}

export interface RecipeNested {
  recipeId: string;
  quantity: number;
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

export interface BOMEntry {
  productId: string;
  quantity: number;
}

export interface RecipeBOM {
  productIds: string[];
  recipeIds: string[];
  bom: BOMEntry[];
}

export interface UpdateResult {
  recipeId: string;
  oldCost: number;
  newCost: number;
  updated: boolean;
  reason: string;
}



