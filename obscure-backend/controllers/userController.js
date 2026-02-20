const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const User = require('../models/User');

const SALT_ROUNDS = 12;
const sign = (payload, opts = {}) =>
  jwt.sign(payload, process.env.JWT_SECRET, { algorithm: 'HS256', expiresIn: '1h', ...opts });

exports.register = async (req, res) => {
  try {
    let { username, password } = req.body || {};
    if (!username || !password) return res.status(400).json({ error: 'username & password required' });
    username = String(username).toLowerCase().trim();

    if (!/^[a-zA-Z0-9_]{3,24}$/.test(username))
      return res.status(400).json({ error: 'Invalid username format' });
    if (String(password).length < 10)
      return res.status(400).json({ error: 'Password too short (min 10 chars)' });

    const exists = await User.findOne({ username }).lean();
    if (exists) return res.status(409).json({ error: 'Username already taken' });

    const passwordHash = await bcrypt.hash(password, SALT_ROUNDS);
    const user = await User.create({ username, passwordHash });

    const token = sign({ sub: user._id.toString(), uname: username, tv: user.tokenVersion });
    res.status(201).json({ token, user: { id: user._id, username: user.username } });
  } catch (e) {
    console.error('register error', e);
    res.status(500).json({ error: 'Server error' });
  }
};

exports.login = async (req, res) => {
  try {
    let { username, password } = req.body || {};
    if (!username || !password) return res.status(400).json({ error: 'username & password required' });
    username = String(username).toLowerCase().trim();

    const user = await User.findOne({ username });
    if (!user) return res.status(401).json({ error: 'Invalid credentials' });

    const ok = await bcrypt.compare(password, user.passwordHash);
    if (!ok) return res.status(401).json({ error: 'Invalid credentials' });

    const token = sign({ sub: user._id.toString(), uname: username, tv: user.tokenVersion });
    res.json({ token, user: { id: user._id, username: user.username } });
  } catch (e) {
    console.error('login error', e);
    res.status(500).json({ error: 'Server error' });
  }
};

exports.me = async (req, res) => {
  res.json({ user: req.user });
};

/** Optional: exchange Firebase ID token -> our JWT (keep both for now) */
exports.exchangeGoogle = async (req, res) => {
  try {
    const { admin } = require('../config/firebase'); // lazy require to avoid hard dep
    if (!admin) return res.status(503).json({ error: 'Firebase not configured' });
    const { idToken } = req.body || {};
    if (!idToken) return res.status(400).json({ error: 'idToken required' });
    const decoded = await admin.auth().verifyIdToken(idToken);
    const username = (decoded.email || decoded.uid).split('@')[0].toLowerCase();

    // upsert user without password (google-auth user)
    let user = await User.findOne({ username });
    if (!user) user = await User.create({ username, passwordHash: await bcrypt.hash(require('crypto').randomBytes(16).toString('hex'), SALT_ROUNDS) });

    const token = sign({ sub: user._id.toString(), uname: username, tv: user.tokenVersion });
    res.json({ token, user: { id: user._id, username: user.username } });
  } catch (e) {
    console.error('google exchange error', e);
    res.status(401).json({ error: 'Invalid Google token' });
  }
};

const crypto = require("crypto");

exports.setPublicKey = async (req, res) => {
  try {
    const { publicKey } = req.body || {};

    // Log who is making the request
    console.log(`[setPublicKey] UserID: ${req.user?.id}`);

    // Log part of the public key
    if (publicKey) {
      console.log(`[setPublicKey] Public key received (first 50 chars): ${publicKey.substring(0, 50)}...`);

      // 🔹 Add fingerprint of the received key
      const fingerprint = crypto.createHash("sha256")
        .update(Buffer.from(publicKey, "base64"))
        .digest("hex");
      console.log(`[setPublicKey] Incoming fingerprint: ${fingerprint}`);
    } else {
      console.warn(`[setPublicKey] No publicKey found in request body`);
    }

    // Validate
    if (!publicKey || typeof publicKey !== 'string' || publicKey.length < 100) {
      console.warn(`[setPublicKey] Invalid publicKey from user ${req.user?.id}`);
      return res.status(400).json({ error: 'Invalid publicKey' });
    }

    // Update DB
    const result = await User.updateOne(
      { _id: req.user.id },
      { $set: { publicKey } }
    );
    console.log(`[setPublicKey] DB update result:`, result);

    // 🔹 Optional: fetch from DB and log its fingerprint too
    const user = await User.findById(req.user.id).lean();
    if (user?.publicKey) {
      const dbFingerprint = crypto.createHash("sha256")
        .update(Buffer.from(user.publicKey, "base64"))
        .digest("hex");
      console.log(`[setPublicKey] DB fingerprint: ${dbFingerprint}`);
    }

    return res.json({ ok: true });
  } catch (e) {
    console.error('[setPublicKey] Error:', e);
    res.status(500).json({ error: 'Server error' });
  }
};


exports.getPublicKey = async (req, res) => {
  try {
    const username = String(req.params.username || '').toLowerCase().trim();
    const other = await User.findOne({ username }).select('publicKey username').lean();
    if (!other || !other.publicKey) return res.status(404).json({ error: 'Not found' });
    return res.json({ username: other.username, publicKey: other.publicKey });
  } catch (e) {
    res.status(500).json({ error: 'Server error' });
  }
};

// ------------------ FCM token handlers ------------------

exports.addFcmToken = async (req, res) => {
  try {
    const { token, deviceId, platform } = req.body || {};
    if (!token) return res.status(400).json({ error: 'token required' });

    const user = await User.findById(req.user.id);
    if (!user) return res.status(404).json({ error: 'user not found' });

    // remove any old entry with same token
    const tokens = user.getFcmTokenObjects().filter(t => t.token !== token);
    tokens.push({
      token,
      deviceId: deviceId || null,
      platform: platform || 'android',
      createdAt: new Date(),
      lastActiveAt: new Date()
    });

    user.fcmTokens = tokens;
    await user.save();

    res.json({ ok: true });
  } catch (e) {
    console.error('addFcmToken error', e);
    res.status(500).json({ error: 'Server error' });
  }
};

exports.removeFcmToken = async (req, res) => {
  try {
    const { token } = req.body || {};
    if (!token) return res.status(400).json({ error: 'token required' });

    // removes if stored as plain string or object form
    await User.updateOne(
      { _id: req.user.id },
      { $pull: { fcmTokens: { $in: [token], token: token } } }
    );

    res.json({ ok: true });
  } catch (e) {
    console.error('removeFcmToken error', e);
    res.status(500).json({ error: 'Server error' });
  }
};
