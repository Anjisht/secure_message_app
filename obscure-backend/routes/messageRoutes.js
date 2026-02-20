const express = require('express');
const { requireAuth } = require('../middleware/socketAuth');
const { getHistory } = require('../controllers/messageController');
const router = express.Router();

router.get('/:roomId', requireAuth, getHistory);

module.exports = router;