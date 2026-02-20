const Room = require('../models/Room');
const Message = require('../models/Message');

exports.getHistory = async (req, res) => {
  try {
    const roomId = String(req.params.roomId || '');
    const before = req.query.before ? new Date(req.query.before) : new Date();
    const limit = Math.min(parseInt(req.query.limit || '50', 10), 200);

    // membership check
    const room = await Room.findOne({
      roomId,
      'members.userId': req.user.id,
      'members.status': 'approved'
    }).select('members').lean();

    if (!room) return res.status(403).json({ error: 'Not a member' });

    // fetch newest->oldest then reverse for UI
    const msgs = await Message.find({
      roomId,
      createdAt: { $lt: before }
    })
      .sort({ createdAt: -1 })
      .limit(limit)
      .lean();

    // we return full keyEnvelope here because the client will pick *their* entry only.
    // If you want extra privacy, return only the caller's encKey:
    //  - find their encKey per message and replace keyEnvelope with one { encKey }.
    res.json({ messages: msgs.reverse() });
  } catch (e) {
    console.error('getHistory error', e);
    res.status(500).json({ error: 'Server error' });
  }
};