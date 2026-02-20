// config/firebase.js
const fs = require('fs');
const path = require('path');

try {
  // If GOOGLE_APPLICATION_CREDENTIALS provided and file doesn't exist,
  // try to create it from an env secret we set in the platform.
  const credPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (credPath && !fs.existsSync(credPath)) {
    // Try base64 secret first (recommended)
    if (process.env.FIREBASE_SA_BASE64) {
      const dir = path.dirname(credPath);
      fs.mkdirSync(dir, { recursive: true });
      const buf = Buffer.from(process.env.FIREBASE_SA_BASE64, 'base64');
      fs.writeFileSync(credPath, buf, { mode: 0o600 });
      console.log('✅ Wrote Firebase service account (from base64 env) to', credPath);
    } else if (process.env.FIREBASE_SA_JSON) {
      // fallback: direct JSON in env (less preferred)
      const dir = path.dirname(credPath);
      fs.mkdirSync(dir, { recursive: true });
      fs.writeFileSync(credPath, process.env.FIREBASE_SA_JSON, { mode: 0o600 });
      console.log('✅ Wrote Firebase service account (from json env) to', credPath);
    } else {
      console.log('ℹ️ No FIREBASE_SA_BASE64 or FIREBASE_SA_JSON env found; not creating cred file');
    }
  }
} catch (e) {
  console.error('⚠️ Error while attempting to write Firebase service account file:', e);
}

let admin = null;
try {
  const firebaseAdmin = require('firebase-admin');
  // If GOOGLE_APPLICATION_CREDENTIALS is set, admin.initializeApp() will pick it up
  if (!firebaseAdmin.apps.length) {
    firebaseAdmin.initializeApp();
  }
  admin = firebaseAdmin;
  console.log('✅ Firebase Admin initialized');
} catch (e) {
  console.log('ℹ️ Firebase Admin not initialized (no credentials). This is OK for now.');
}
module.exports =  admin ;