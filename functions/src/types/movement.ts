import * as admin from "firebase-admin";

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



