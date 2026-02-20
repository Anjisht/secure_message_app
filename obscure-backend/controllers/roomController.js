const Room = require('../models/Room');
const bcrypt = require('bcryptjs');
const { nanoid } = require('nanoid');
const User = require('../models/User');

// utility: random alias
function randomAlias() {
  const words = ["Banana","Chair","Apple","Desk","Tiger","Moon","Rocket","Stone","Piano","Cloud"];
  return words[Math.floor(Math.random() * words.length)] + Math.floor(Math.random() * 100);
}

// stable key "smallerId|biggerId"
function makePairKey(a, b) {
  const sA = String(a), sB = String(b);
  return sA < sB ? `${sA}|${sB}` : `${sB}|${sA}`;
}

exports.createRoom = async (req, res) => {
  try {
    const { codePhrase, durationMinutes } = req.body || {};
    const roomId = nanoid(8);
    let hash = null;
    if (codePhrase) {
      hash = await bcrypt.hash(codePhrase, 10);
    }
    const expiresAt = durationMinutes
      ? new Date(Date.now() + durationMinutes * 60000)
      : null;

    const alias = randomAlias();
    const room = await Room.create({
      roomId,
      adminId: req.user.id,
      codePhraseHash: hash,
      expiresAt,
      members: [{ userId: req.user.id, alias, status: 'approved' }]
    });
    res.json({ roomId: room.roomId, alias, expiresAt });
  } catch (e) {
    console.error('createRoom error', e);
    res.status(500).json({ error: 'Server error' });
  }
};

exports.requestJoin = async (req, res) => {
  try {
    const { roomId, codePhrase, joinNote } = req.body || {};
    const room = await Room.findOne({ roomId });
    if (!room) return res.status(404).json({ error: 'Room not found' });
    if (room.expiresAt && room.expiresAt < new Date())
      return res.status(400).json({ error: 'Room expired' });

    // ❌ REMOVE validation check – just record what the user typed
    const typedPhrase = codePhrase ? String(codePhrase).slice(0, 280) : null;

    // ✅ See if user already in members
    const existing = room.members.find(m => String(m.userId) === String(req.user.id));
    if (existing) {
      if (existing.status === 'pending') {
        // update note + codePhrase if provided
        if (joinNote) existing.joinNote = String(joinNote).slice(0, 280);
        if (typedPhrase) existing.typedPhrase = typedPhrase;
        await room.save();
      }
      return res.json({
        message: existing.status === 'approved' ? 'Already a member' : 'Join request already pending',
        status: existing.status
      });
    }

    const alias = randomAlias();
    room.members.push({
      userId: req.user.id,
      alias,
      status: 'pending',
      joinNote: joinNote ? String(joinNote).slice(0, 280) : null,
      typedPhrase // <-- store what they entered
    });

    await room.save();

    res.json({ message: 'Join request submitted', status: 'pending', alias });
  } catch (e) {
    console.error('requestJoin error', e);
    res.status(500).json({ error: 'Server error' });
  }
};


exports.approveMember = async (req, res) => {
  try {
    const { roomId, memberId } = req.body || {};
    const room = await Room.findOne({ roomId });
    if (!room) return res.status(404).json({ error: 'Room not found' });
    if (room.adminId.toString() !== req.user.id)
      return res.status(403).json({ error: 'Only admin can approve' });

    const member = room.members.find(m => m.userId.toString() === memberId);
    if (!member) return res.status(404).json({ error: 'Member not found' });
    member.status = 'approved';
    await room.save();

    res.json({ message: 'Member approved', alias: member.alias });
  } catch (e) {
    console.error('approveMember error', e);
    res.status(500).json({ error: 'Server error' });
  }
};

exports.listMyRooms = async (req, res) => {
  try {
    const rooms = await Room.find({ 'members.userId': req.user.id })
      .select('roomId expiresAt members alias')
      .lean();

    const result = rooms.map(r => ({
      roomId: r.roomId,
      expiresAt: r.expiresAt,
      alias: r.alias,
      members: r.members.map(m => ({
        userId: String(m.userId),
        alias: m.alias,
        status: m.status
      }))
    }));

    res.json({ rooms: result });
  } catch (e) {
    console.error('listMyRooms error', e);
    res.status(500).json({ error: 'Server error' });
  }
};


exports.getRoomMembers = async (req, res) => {
  try {
    const roomId = String(req.params.roomId || '');
    const room = await Room.findOne({
      roomId,
      'members.userId': req.user.id,
      'members.status': 'approved'
    }).lean();
    if (!room) return res.status(403).json({ error: 'Not a member' });

    // fetch public keys for all approved members
    const approved = room.members.filter(m => m.status === 'approved');
    const userIds = approved.map(m => m.userId);
    const users = await User.find({ _id: { $in: userIds } })
      .select('_id publicKey username')
      .lean();

    // join members with public keys
    const members = approved.map(m => {
      const u = users.find(x => String(x._id) === String(m.userId));
      return {
        userId: String(m.userId),
        alias: m.alias,
        username: u?.username || null,
        publicKey: u?.publicKey || null   // might be null if user hasn’t uploaded yet
      };
    });

    res.json({ roomId, members });
  } catch (e) {
    console.error('getRoomMembers error', e);
    res.status(500).json({ error: 'Server error' });
  }
};

/** Returns full room info to members (admin flag + all members with status). */
exports.getRoomInfo = async (req, res) => {
  try {
    const roomId = String(req.params.roomId || '');
    const room = await Room.findOne({ roomId }).lean();
    if (!room) return res.status(404).json({ error: 'Room not found' });

    const isMember = room.members.some(m => String(m.userId) === String(req.user.id));
    if (!isMember) return res.status(403).json({ error: 'Not a member' });

    const isAdmin = String(room.adminId) === String(req.user.id);

    const members = room.members.map(m => ({
      userId: String(m.userId),
      alias: m.alias,
      status: m.status,
      requestedAt: m.requestedAt || null,
      // joinNote is shown only to admin
      joinNote: isAdmin ? (m.joinNote || null) : null
      // usernames are masked by design in rooms; we don't return them
    }));

    res.json({
      roomId: room.roomId,
      isAdmin,
      expiresAt: room.expiresAt || null,
      members
    });
  } catch (e) {
    console.error('getRoomInfo error', e);
    res.status(500).json({ error: 'Server error' });
  }
};

exports.denyMember = async (req, res) => {
  try {
    const { roomId, memberId } = req.body || {};
    const room = await Room.findOne({ roomId });
    if (!room) return res.status(404).json({ error: 'Room not found' });
    if (String(room.adminId) !== String(req.user.id))
      return res.status(403).json({ error: 'Only admin can deny' });

    const idx = room.members.findIndex(
      m => String(m.userId) === String(memberId) && m.status === 'pending'
    );
    if (idx === -1) return res.status(404).json({ error: 'Pending member not found' });

    room.members.splice(idx, 1);
    await room.save();
    res.json({ message: 'Member denied' });
  } catch (e) {
    console.error('denyMember error', e);
    res.status(500).json({ error: 'Server error' });
  }
};

/** POST /api/dm/start { targetUsername? , targetUserId? } => { roomId } */
exports.startDm = async (req, res) => {
  try {
    const meId = req.user.id;
    const { targetUsername, targetUserId } = req.body || {};
    if (!targetUsername && !targetUserId) {
      return res.status(400).json({ error: 'Provide targetUsername or targetUserId' });
    }

    const target = targetUserId
      ? await User.findById(targetUserId).select('_id').lean()
      : await User.findOne({ username: String(targetUsername).toLowerCase().trim() }).select('_id').lean();

    if (!target) return res.status(404).json({ error: 'Target user not found' });
    if (String(target._id) === String(meId)) return res.status(400).json({ error: 'Cannot start DM with yourself' });

    const pairKey = makePairKey(meId, target._id);

    // find existing
    let dm = await Room.findOne({ type: 'dm', pairKey }).lean();
    if (dm) {
      return res.json({ roomId: dm.roomId, type: 'dm', reused: true });
    }

    // create new dm (both approved; admin is requester for consistency)
    const roomId = nanoid(10);
    dm = await Room.create({
      roomId,
      type: 'dm',
      pairKey,
      adminId: meId,
      members: [
        { userId: meId, alias: randomAlias(), status: 'approved', approvedAt: new Date() },
        { userId: target._id, alias: randomAlias(), status: 'approved', approvedAt: new Date() },
      ]
    });

    return res.json({ roomId: dm.roomId, type: 'dm', reused: false });
  } catch (e) {
    console.error('startDm error', e);
    return res.status(500).json({ error: 'Server error' });
  }
};