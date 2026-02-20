const express = require('express');
const { requireAuth } = require('../middleware/socketAuth');
const {
  createRoom, requestJoin, approveMember, listMyRooms, getRoomMembers, getRoomInfo, denyMember, startDm
} = require('../controllers/roomController');

const router = express.Router();

router.post('/create', requireAuth, createRoom);
router.post('/join', requireAuth, requestJoin);
router.post('/approve', requireAuth, approveMember);
router.get('/mine', requireAuth, async (req, res, next) => {
  // wrapper to include "type" field in selection
  try {
    const Room = require('../models/Room');
    const rooms = await Room.find({ 'members.userId': req.user.id })
      .select('roomId type expiresAt members')
      .lean();
    res.json({ rooms });
  } catch (e) { next(e); }
});
router.get('/:roomId/members', requireAuth, getRoomMembers);
router.post('/deny', requireAuth, denyMember);         
router.get('/:roomId/info', requireAuth, getRoomInfo);
// start DM
router.post('/dm/start', requireAuth, startDm);

module.exports = router;