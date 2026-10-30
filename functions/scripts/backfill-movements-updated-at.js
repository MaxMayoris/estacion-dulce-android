/**
 * One-off backfill: sets `updatedAt` on every movement that doesn't have it, so the app's
 * incremental sync (query by `updatedAt`) includes it. Idempotent: safe to run more than once.
 *
 * Uses Application Default Credentials (`gcloud auth application-default login`).
 *
 * Usage (from functions/):
 *   node scripts/backfill-movements-updated-at.js <projectId> [--dry-run]
 *
 * Cost: one read per movement plus one write per movement missing `updatedAt`.
 */
const admin = require("firebase-admin");

const projectId = process.argv[2];
const dryRun = process.argv.includes("--dry-run");

if (!projectId || projectId.startsWith("--")) {
  console.error("Usage: node scripts/backfill-movements-updated-at.js <projectId> [--dry-run]");
  process.exit(1);
}

admin.initializeApp({ projectId });
const db = admin.firestore();

async function main() {
  const snapshot = await db.collection("movements").get();
  const missing = snapshot.docs.filter((doc) => doc.get("updatedAt") === undefined);

  console.log(`Project ${projectId}: ${snapshot.size} movements, ${missing.length} without updatedAt`);

  if (dryRun || missing.length === 0) {
    console.log(dryRun ? "Dry run: nothing written." : "Nothing to do.");
    return;
  }

  const writer = db.bulkWriter();
  missing.forEach((doc) => {
    writer.update(doc.ref, { updatedAt: admin.firestore.FieldValue.serverTimestamp() });
  });
  await writer.close();

  console.log(`Done: updatedAt set on ${missing.length} movements.`);
}

main().catch((error) => {
  console.error("Backfill failed:", error);
  process.exit(1);
});
