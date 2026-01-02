import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions/v2";
import * as admin from "firebase-admin";

interface KitchenOrder {
  name: string;
  quantity: number;
  status: string;
}

interface PendingOrderGroup {
  movementId: string;
  orders: KitchenOrder[];
  clientName: string;
}

/**
 * Core logic for checking pending orders and sending notifications.
 * Can be used by both scheduled and callable functions.
 */
async function checkAndSendPendingOrdersNotification(): Promise<{ success: boolean; message: string; pendingOrdersCount: number; movementsCount: number }> {
  try {
    const db = admin.firestore();
    
    // Get all SALE movements where kitchenOrderStatus is not READY
    const movementsSnapshot = await db.collection("movements")
      .where("type", "==", "SALE")
      .get();

    if (movementsSnapshot.empty) {
      return {
        success: false,
        message: "No SALE movements found",
        pendingOrdersCount: 0,
        movementsCount: 0
      };
    }

    const pendingOrders: PendingOrderGroup[] = [];

    // Process each movement
    for (const movementDoc of movementsSnapshot.docs) {
      const movementData = movementDoc.data();
      const kitchenOrderStatus = movementData.kitchenOrderStatus;

      // Skip if status is READY, DONE, CANCELED, or null
      if (!kitchenOrderStatus || 
          kitchenOrderStatus === "READY" || 
          kitchenOrderStatus === "DONE" || 
          kitchenOrderStatus === "CANCELED") {
        continue;
      }

      const movementId = movementDoc.id;
      
      // Get kitchenOrders from subcollection for PENDING and PREPARING statuses
      const kitchenOrdersSnapshot = await db.collection("movements")
        .doc(movementId)
        .collection("kitchenOrders")
        .get();

      const kitchenOrders = kitchenOrdersSnapshot.docs
        .map(doc => {
          const data = doc.data();
          return {
            name: data.name || "Sin nombre",
            quantity: data.quantity || 0,
            status: data.status || "PENDING"
          };
        })
        .filter(order => order.status !== "READY" && order.status !== "DONE");

      if (kitchenOrders.length > 0) {
        const personId = movementData.personId;
        let clientName = "Cliente";
        
        if (personId) {
          try {
            const personDoc = await db.collection("persons").doc(personId).get();
            if (personDoc.exists) {
              const personData = personDoc.data();
              const firstName = personData?.name || "";
              const lastName = personData?.lastName || "";
              clientName = `${firstName} ${lastName}`.trim() || "Cliente";
            }
          } catch (error) {
            logger.error(`Error fetching person ${personId}:`, error);
          }
        }
        
        pendingOrders.push({
          movementId: movementId,
          orders: kitchenOrders,
          clientName: clientName
        });
      }
    }

    // If no pending orders, send administrative tasks notification
    if (pendingOrders.length === 0) {
      const title = "✅ Todo al día";
      const body = "No hay pedidos pendientes. ¿Has revisado hoy?\n\n- Precios de productos\n- Stock disponible\n- Imágenes de productos\n- Página web";

      if (!title || !body || title.trim() === "" || body.trim() === "") {
        logger.warn("Notification title or body is empty (no pending orders case)");
        return {
          success: false,
          message: "Notification title or body is empty",
          pendingOrdersCount: 0,
          movementsCount: 0
        };
      }

      try {
        await admin.messaging().send({
          topic: "pending_orders",
          notification: {
            title: title,
            body: body
          },
          data: {
            screen: "home"
          },
          android: {
            priority: "high" as const,
            notification: {
              channelId: "estacion_dulce_general"
            }
          }
        });

        return {
          success: true,
          message: "Notification sent: No pending orders",
          pendingOrdersCount: 0,
          movementsCount: 0
        };
      } catch (error) {
        logger.error("Error sending notification (no pending orders):", error);
        throw error;
      }
    }

    // Build notification message for pending orders
    const totalOrders = pendingOrders.reduce((sum, movement) => sum + movement.orders.length, 0);
    const totalMovements = pendingOrders.length;

    if (totalOrders === 0) {
      logger.warn("Pending orders array is not empty but totalOrders is 0, this should not happen");
      const title = "✅ Todo al día";
      const body = "No hay pedidos pendientes. ¿Has revisado hoy?\n\n- Precios de productos\n- Stock disponible\n- Imágenes de productos\n- Página web";

      try {
        await admin.messaging().send({
          topic: "pending_orders",
          notification: {
            title: title,
            body: body
          },
          data: {
            screen: "home"
          },
          android: {
            priority: "high" as const,
            notification: {
              channelId: "estacion_dulce_general"
            }
          }
        });

        return {
          success: true,
          message: "Notification sent: No pending orders (fallback)",
          pendingOrdersCount: 0,
          movementsCount: 0
        };
      } catch (error) {
        logger.error("Error sending notification (fallback):", error);
        throw error;
      }
    }

    let body = `Tienes ${totalOrders} pedido${totalOrders !== 1 ? "s" : ""} pendiente${totalOrders !== 1 ? "s" : ""} en ${totalMovements} venta${totalMovements !== 1 ? "s" : ""}:\n\n`;

    // Add orders list (limit to avoid message too long)
    const maxItems = 10;
    let itemsAdded = 0;

    for (const movement of pendingOrders) {
      if (itemsAdded >= maxItems) {
        const remaining = totalOrders - itemsAdded;
        body += `\n... y ${remaining} pedido${remaining !== 1 ? "s" : ""} más`;
        break;
      }

      for (const order of movement.orders) {
        if (itemsAdded >= maxItems) {
          const remaining = totalOrders - itemsAdded;
          body += `\n... y ${remaining} pedido${remaining !== 1 ? "s" : ""} más`;
          break;
        }
        body += `- ${order.name} (x${order.quantity}) para ${movement.clientName}\n`;
        itemsAdded++;
      }
      if (itemsAdded >= maxItems) break;
    }

    const title = "🍰 Pedidos Pendientes";
    const trimmedBody = body.trim();

    if (!title || !trimmedBody || title.trim() === "" || trimmedBody === "") {
      return {
        success: false,
        message: "Notification title or body is empty",
        pendingOrdersCount: totalOrders,
        movementsCount: totalMovements
      };
    }

    try {
      await admin.messaging().send({
        topic: "pending_orders",
        notification: {
          title: title,
          body: trimmedBody
        },
        data: {
          screen: "kitchen_orders"
        },
        android: {
          priority: "high" as const,
          notification: {
            channelId: "estacion_dulce_general"
          }
        }
      });
      
      return {
        success: true,
        message: `Notification sent: ${totalOrders} orders in ${totalMovements} movements`,
        pendingOrdersCount: totalOrders,
        movementsCount: totalMovements
      };
    } catch (error) {
      logger.error("Error sending pending orders notification:", error);
      throw error;
    }
  } catch (error) {
    logger.error("Error in pending orders notification:", error);
    throw error;
  }
}

/**
 * Scheduled Cloud Function that runs daily at 7 AM.
 * Sends a push notification with all pending kitchen orders for the day.
 */
export const onPendingOrdersDailyNotification = onSchedule(
  {
    schedule: "0 7 * * *", // Daily at 07:00 AM
    timeZone: "America/Argentina/Buenos_Aires", // UTC-3
    region: "southamerica-east1",
    timeoutSeconds: 60
  },
  async () => {
    await checkAndSendPendingOrdersNotification();
  }
);

