#!/usr/bin/env node

/**
 * Script de verificación para Firebase Functions Gen 2
 * Verifica que la configuración esté correcta antes del despliegue
 */

const fs = require("fs");
const path = require("path");

console.log("🔍 Verificando configuración de Firebase Functions Gen 2...\n");

// Verificar archivos necesarios
const requiredFiles = [
  "../package.json",
  "../tsconfig.json",
  "../src/index.ts",
  "../../firebase.json",
  "../../.firebaserc"
];

let allFilesExist = true;

requiredFiles.forEach(file => {
  const filePath = path.resolve(__dirname, file);
  if (fs.existsSync(filePath)) {
    console.log(`✅ ${file} - OK`);
  } else {
    console.log(`❌ ${file} - FALTANTE`);
    allFilesExist = false;
  }
});

// Verificar package.json
if (fs.existsSync(path.resolve(__dirname, "../package.json"))) {
  const packageJson = JSON.parse(fs.readFileSync(path.resolve(__dirname, "../package.json"), "utf8"));
  
  console.log("\n📦 Verificando dependencias:");
  
  if (packageJson.dependencies["firebase-functions"] && packageJson.dependencies["firebase-functions"].startsWith("^5.")) {
    console.log("✅ firebase-functions v5.x - OK (Gen 2)");
  } else {
    console.log("❌ firebase-functions - Versión incorrecta (debe ser v5.x para Gen 2)");
    allFilesExist = false;
  }
  
  if (packageJson.dependencies["firebase-admin"]) {
    console.log("✅ firebase-admin - OK");
  } else {
    console.log("❌ firebase-admin - FALTANTE");
    allFilesExist = false;
  }
}

// Verificar firebase.json
if (fs.existsSync(path.resolve(__dirname, "../../firebase.json"))) {
  const firebaseJson = JSON.parse(fs.readFileSync(path.resolve(__dirname, "../../firebase.json"), "utf8"));
  
  console.log("\n⚙️ Verificando firebase.json:");
  
  if (firebaseJson.functions && firebaseJson.functions[0]) {
    const functionsConfig = firebaseJson.functions[0];
    
    if (functionsConfig.runtime === "nodejs18") {
      console.log("✅ Runtime nodejs18 - OK");
    } else {
      console.log("❌ Runtime - Debe ser nodejs18");
      allFilesExist = false;
    }
    
    if (functionsConfig.source === "functions") {
      console.log("✅ Source directory - OK");
    } else {
      console.log("❌ Source directory - Debe ser \"functions\"");
      allFilesExist = false;
    }
  }
}

// Verificar .firebaserc
if (fs.existsSync(path.resolve(__dirname, "../../.firebaserc"))) {
  const firebaserc = JSON.parse(fs.readFileSync(path.resolve(__dirname, "../../.firebaserc"), "utf8"));
  
  console.log("\n🌍 Verificando configuración de entornos:");
  
  if (firebaserc.projects) {
    if (firebaserc.projects.dev) {
      console.log(`✅ Entorno dev: ${firebaserc.projects.dev}`);
    } else {
      console.log("❌ Entorno dev - FALTANTE");
      allFilesExist = false;
    }
    
    if (firebaserc.projects.prod) {
      console.log(`✅ Entorno prod: ${firebaserc.projects.prod}`);
    } else {
      console.log("❌ Entorno prod - FALTANTE");
      allFilesExist = false;
    }
  }
}

// Verificar código TypeScript (buscar en toda la carpeta src)
console.log("\n💻 Verificando código TypeScript:");

const checkFilesRecursively = (dir) => {
  let allContent = "";
  const files = fs.readdirSync(dir);
  
  files.forEach(file => {
    const filePath = path.join(dir, file);
    const stat = fs.statSync(filePath);
    
    if (stat.isDirectory()) {
      allContent += checkFilesRecursively(filePath);
    } else if (file.endsWith(".ts")) {
      allContent += fs.readFileSync(filePath, "utf8") + "\n";
    }
  });
  
  return allContent;
};

if (fs.existsSync(path.resolve(__dirname, "../src"))) {
  const allCode = checkFilesRecursively(path.resolve(__dirname, "../src"));
  
  if (allCode.includes("firebase-functions/v2/firestore") || allCode.includes("firebase-functions/v2")) {
    console.log("✅ Import Gen 2 - OK");
  } else {
    console.log("❌ Import Gen 2 - Debe usar firebase-functions/v2");
    allFilesExist = false;
  }
  
  if (allCode.includes("logger") && allCode.includes("firebase-functions/v2")) {
    console.log("✅ Logger Gen 2 - OK");
  } else {
    console.log("❌ Logger Gen 2 - Debe usar firebase-functions/v2");
    allFilesExist = false;
  }
  
  if (allCode.includes("functions.firestore.document")) {
    console.log("❌ Código Gen 1 detectado - Debe usar onDocumentUpdated");
    allFilesExist = false;
  } else {
    console.log("✅ Sin código Gen 1 - OK");
  }
  
  if (fs.existsSync(path.resolve(__dirname, "../lib/index.js"))) {
    console.log("✅ Código compilado - OK");
  } else {
    console.log("⚠️ Código no compilado - Ejecuta 'npm run build'");
  }
}

// Resultado final
console.log("\n" + "=".repeat(50));

if (allFilesExist) {
  console.log("🎉 ¡Configuración correcta! Listo para desplegar.");
  console.log("\n📋 Próximos pasos:");
  console.log("1. npm install");
  console.log("2. npm run build");
  console.log("3. firebase use dev (o prod)");
  console.log("4. firebase deploy --only functions");
} else {
  console.log("❌ Configuración incompleta. Revisa los errores arriba.");
  process.exit(1);
}

