const s3 = require('../config/spaces');
const { nanoid } = require('nanoid');

exports.getUploadUrl = async (req, res) => {
  try {
    const { contentType } = req.body;
    if (!contentType) return res.status(400).json({ error: 'contentType required' });

    const fileKey = `uploads/${nanoid(12)}.${contentType.split('/')[1]}`;

    const params = {
      Bucket: process.env.DO_SPACES_BUCKET, // e.g. "baatcheet"
      Key: fileKey,
      ContentType: contentType,
      // ACL: 'private', // keep encrypted files private
    };

    const uploadUrl = await s3.getSignedUrlPromise('putObject', {
      ...params,
      Expires: 60 * 5, // valid for 5 minutes
    });

    // The public (CDN) URL can be built like this:
    const fileUrl = `https://${process.env.DO_SPACES_CDN}/${fileKey}`;

    res.json({ uploadUrl, fileUrl, fileKey });
  } catch (err) {
    console.error('getUploadUrl error:', err);
    res.status(500).json({ error: 'Error creating presigned URL' });
  }
};

exports.getDownloadUrl = async (req, res) => {
  try {
    const { fileKey } = req.query;
    if (!fileKey) return res.status(400).json({ error: 'fileKey required' });

    const params = {
      Bucket: process.env.DO_SPACES_BUCKET,
      Key: fileKey,
      Expires: 60 * 5, // valid 5 mins
    };

    const downloadUrl = await s3.getSignedUrlPromise('getObject', params);
    res.json({ downloadUrl });
  } catch (err) {
    console.error('getDownloadUrl error:', err);
    res.status(500).json({ error: 'Error creating download URL' });
  }
};