require('dotenv').config();
const s3 = require('./config/spaces');

const params = {
  Bucket: process.env.DO_SPACES_BUCKET
};

s3.listObjectsV2(params, (err, data) => {
  if (err) console.error('❌ Error:', err);
  else console.log('✅ Connected! Files:', data.Contents);
});