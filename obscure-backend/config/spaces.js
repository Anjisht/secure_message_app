const AWS = require('aws-sdk');

// DigitalOcean Spaces endpoint — e.g. "blr1.digitaloceanspaces.com"
const spacesEndpoint = new AWS.Endpoint(process.env.DO_SPACES_ENDPOINT);

const s3 = new AWS.S3({
  endpoint: spacesEndpoint,
  accessKeyId: process.env.DO_SPACES_KEY,
  secretAccessKey: process.env.DO_SPACES_SECRET,
  signatureVersion: 'v4', // required for presigned URLs
});

module.exports = s3;