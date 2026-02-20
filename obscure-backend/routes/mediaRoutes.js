const express = require('express');
const router = express.Router();
const { getUploadUrl, getDownloadUrl } = require('../controllers/mediaController');
const { requireAuth } = require('../middleware/socketAuth');

router.post('/upload-url', requireAuth, getUploadUrl);
router.get('/download-url', requireAuth, getDownloadUrl);

module.exports = router;