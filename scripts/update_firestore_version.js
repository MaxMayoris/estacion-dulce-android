const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const https = require('https');

function getVersionCode() {
  const gradlePath = path.join(__dirname, '../app/build.gradle.kts');
  if (!fs.existsSync(gradlePath)) {
    throw new Error(`Gradle file not found at ${gradlePath}`);
  }
  const content = fs.readFileSync(gradlePath, 'utf8');
  const match = content.match(/versionCode\s*=\s*(\d+)/);
  if (!match) {
    throw new Error('Could not find versionCode in build.gradle.kts');
  }
  return parseInt(match[1], 10);
}

function signJwt(serviceAccount) {
  const header = JSON.stringify({ alg: 'RS256', typ: 'JWT' });
  const payload = JSON.stringify({
    iss: serviceAccount.client_email,
    scope: 'https://www.googleapis.com/auth/cloud-platform',
    aud: 'https://oauth2.googleapis.com/token',
    exp: Math.floor(Date.now() / 1000) + 3600,
    iat: Math.floor(Date.now() / 1000),
  });

  const base64UrlHeader = Buffer.from(header).toString('base64url');
  const base64UrlPayload = Buffer.from(payload).toString('base64url');
  const signatureInput = `${base64UrlHeader}.${base64UrlPayload}`;

  const signer = crypto.createSign('RSA-SHA256');
  signer.update(signatureInput);
  const signature = signer.sign(serviceAccount.private_key, 'base64url');

  return `${signatureInput}.${signature}`;
}

function getAccessToken(jwtToken) {
  return new Promise((resolve, reject) => {
    const postData = `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwtToken}`;
    const req = https.request('https://oauth2.googleapis.com/token', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'Content-Length': postData.length,
      },
    }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const json = JSON.parse(data);
          if (json.access_token) resolve(json.access_token);
          else reject(json);
        } catch (e) { reject(e); }
      });
    });
    req.on('error', reject);
    req.write(postData);
    req.end();
  });
}

function updateFirestore(projectId, token, minVersion, updateUrl) {
  const docPath = `projects/${projectId}/databases/(default)/documents/settings/app_config`;
  const body = JSON.stringify({
    fields: {
      minVersionCode: { integerValue: String(minVersion) },
      updateUrl: { stringValue: updateUrl }
    }
  });

  return new Promise((resolve, reject) => {
    const url = `https://firestore.googleapis.com/v1/${docPath}?updateMask.fieldPaths=minVersionCode&updateMask.fieldPaths=updateUrl`;
    const req = https.request(url, {
      method: 'PATCH',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json',
        'Content-Length': body.length,
      },
    }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const json = JSON.parse(data);
          resolve({ statusCode: res.statusCode, data: json });
        } catch (e) { reject(e); }
      });
    });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

async function main() {
  console.log('--- ACTUALIZACIÓN AUTOMÁTICA DE FIRESTORE ---');
  try {
    const versionCode = getVersionCode();
    console.log(`Versión detectada en build.gradle.kts: ${versionCode}`);

    const credsPath = path.join(__dirname, '../../estacion-dulce-keys/play-store-credentials.json');
    if (!fs.existsSync(credsPath)) {
      console.warn(`[WARNING] No se encontraron credenciales de cuenta de servicio en: ${credsPath}`);
      console.warn('Omitiendo actualización automática de Firestore.');
      return;
    }

    const creds = JSON.parse(fs.readFileSync(credsPath, 'utf8'));
    console.log(`Conectando a proyecto: ${creds.project_id}`);

    const jwt = signJwt(creds);
    const token = await getAccessToken(jwt);

    const updateUrl = "https://play.google.com/apps/internaltest/4701724229366743626";
    const result = await updateFirestore(creds.project_id, token, versionCode, updateUrl);

    if (result.statusCode === 200) {
      console.log('✅ Firestore actualizado con éxito.');
      console.log(`minVersionCode = ${versionCode}`);
    } else {
      console.error(`❌ Error al actualizar Firestore (Status ${result.statusCode}):`, JSON.stringify(result.data, null, 2));
      if (result.statusCode === 403) {
        console.error('\n[IMPORTANTE] Asegúrate de que play-publisher@estaciondulceprod.iam.gserviceaccount.com tenga el rol "Administrador de Cloud Datastore" en Google Cloud Console.');
      }
    }
  } catch (error) {
    console.error('❌ Error en script de actualización de Firestore:', error.message);
  }
}

main();
