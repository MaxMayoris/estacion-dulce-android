const admin = require('firebase-admin');
const sharp = require('sharp');
const path = require('path');
const os = require('os');
const fs = require('fs');

// Initialize Firebase Admin with default credentials
// Using environment variable for the bucket name to allow switching between Dev and Prod
const BUCKET_NAME = process.env.BUCKET_NAME || 'estaciondulceappdev.appspot.com';

admin.initializeApp({
  storageBucket: BUCKET_NAME
});

const bucket = admin.storage().bucket();

const FOLDERS_TO_OPTIMIZE = ['recipes/', 'movements/'];
const SIZE_THRESHOLD_BYTES = 500 * 1024; // 500 KB
const MAX_WIDTH = 1200;
const QUALITY = 75;

async function optimizeImages() {
  console.log('Starting Image Optimization Job...');

  for (const folder of FOLDERS_TO_OPTIMIZE) {
    console.log(`Scanning folder: ${folder}`);

    try {
      const [files] = await bucket.getFiles({ prefix: folder });

      for (const file of files) {
        // Skip directories and small files
        if (file.name.endsWith('/') || file.metadata.size < SIZE_THRESHOLD_BYTES) {
          continue;
        }

        // Only process common image formats (could be refined)
        const ext = path.extname(file.name).toLowerCase();
        if (!['.jpg', '.jpeg', '.png', '.webp'].includes(ext)) {
          console.log(`Skipping non-image file: ${file.name}`);
          continue;
        }

        await processFile(file);
      }
    } catch (error) {
      console.error(`Error scanning folder ${folder}:`, error);
    }
  }

  console.log('Image Optimization Job finished.');
}

async function processFile(file) {
  const tempFilePath = path.join(os.tmpdir(), path.basename(file.name));
  const outputFilePath = path.join(os.tmpdir(), `optimized_${path.basename(file.name)}`);

  try {
    const oldSizeMB = (file.metadata.size / (1024 * 1024)).toFixed(2);

    // Download file
    await file.download({ destination: tempFilePath });

    // Optimize with Sharp
    const sharpInstance = sharp(tempFilePath);
    const metadata = await sharpInstance.metadata();

    let pipeline = sharpInstance;
    if (metadata.width > MAX_WIDTH) {
      pipeline = pipeline.resize(MAX_WIDTH);
    }

    await pipeline
      .rotate() // Auto-rotate based on EXIF orientation
      .webp({ quality: QUALITY })
      .toFile(outputFilePath);

    const newSizeStat = fs.statSync(outputFilePath);
    const newSizeKB = (newSizeStat.size / 1024).toFixed(0);

    // Upload back to the same path (preserving metadata like content type)
    await bucket.upload(outputFilePath, {
      destination: file.name,
      contentType: 'image/webp',
      metadata: {
        // We can keep original metadata if needed, but 'contentType' is critical
        cachedControl: file.metadata.cacheControl || 'public, max-age=31536000',
      }
    });

    console.log(`[OPTIMIZED] ${file.name}: ${oldSizeMB}MB -> ${newSizeKB}KB`);

  } catch (error) {
    console.error(`Failed to process ${file.name}:`, error);
  } finally {
    // Cleanup temp files
    if (fs.existsSync(tempFilePath)) fs.unlinkSync(tempFilePath);
    if (fs.existsSync(outputFilePath)) fs.unlinkSync(outputFilePath);
  }
}

// Run the job
optimizeImages().catch(console.error);
