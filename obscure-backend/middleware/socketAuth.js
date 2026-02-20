const jwt = require('jsonwebtoken');
const User = require('../models/User');

function verifyJwt(token) {
  return jwt.verify(token, process.env.JWT_SECRET, { algorithms: ['HS256'] });
}

// HTTP middleware
async function requireAuth(req, res, next) {
  try {
    const auth = req.headers.authorization || '';
    const token = auth.startsWith('Bearer ') ? auth.slice(7) : null;
    if (!token) return res.status(401).json({ error: 'Missing token' });

    const decoded = verifyJwt(token);
    const user = await User.findById(decoded.sub).select('_id username tokenVersion');
    if (!user) return res.status(401).json({ error: 'Invalid token' });
    if (decoded.tv != null && decoded.tv !== user.tokenVersion)
      return res.status(401).json({ error: 'Token revoked' });

    req.user = { id: user._id.toString(), username: user.username };
    next();
  } catch (e) {
    return res.status(401).json({ error: 'Unauthorized' });
  }
}

// Socket.IO guard (for later)
function verifySocketAuth(socket, next) {
  try {
    const token = socket.handshake.auth?.token
      || (socket.handshake.headers.authorization || '').replace(/^Bearer /, '');
    if (!token) return next(new Error('Missing token'));
    const decoded = verifyJwt(token);
    socket.user = { id: decoded.sub, username: decoded.uname };
    next();
  } catch (e) {
    next(new Error('Unauthorized'));
  }
}

module.exports = { requireAuth, verifySocketAuth };