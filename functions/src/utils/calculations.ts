/**
 * Calculate profit percentage based on cost and sale price
 */
export function calculateProfitPercentage(cost: number, salePrice: number): number {
  if (cost <= 0) return 0;
  return ((salePrice - cost) / cost) * 100;
}



