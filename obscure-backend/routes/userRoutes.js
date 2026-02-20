const express = require('express');
const { register, login, me, exchangeGoogle, setPublicKey, getPublicKey, addFcmToken, removeFcmToken } = require('../controllers/userController');
const { requireAuth } = require('../middleware/socketAuth');

const router = express.Router();

router.post('/register', register);
router.post('/login', login);
router.get('/me', requireAuth, me);

router.post('/public-key', requireAuth, setPublicKey);
router.get('/public-key/:username', requireAuth, getPublicKey); // for later (fetch others' keys)

// keep both auths for now (optional)
router.post('/google/exchange', exchangeGoogle);

router.post('/fcm/add', requireAuth, addFcmToken);
router.post('/fcm/remove', requireAuth, removeFcmToken);

module.exports = router;