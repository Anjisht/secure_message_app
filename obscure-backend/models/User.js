const mongoose = require('mongoose');

// per-device token object (keeps _id off embedded docs)
const FcmTokenSchema = new mongoose.Schema({
  token:       { type: String, required: true, index: true },
  deviceId:    { type: String, default: null },
  platform:    { type: String, default: 'android' },
  createdAt:   { type: Date,   default: Date.now },
  lastActiveAt:{ type: Date,   default: Date.now }
}, { _id: false });

const userSchema = new mongoose.Schema({
  username: {
    type: String, required: true, unique: true, index: true,
    lowercase: true, trim: true, minlength: 3, maxlength: 24
  },
  passwordHash: { type: String, required: true },
  publicKey: { type: String },                 // will be used later for E2EE
  fcmTokens: { type: [mongoose.Schema.Types.Mixed], default: [] },
  tokenVersion: { type: Number, default: 0 }   // optional: JWT invalidation
}, { timestamps: true });

// helper to read tokens as objects (auto-wrap legacy strings)
userSchema.methods.getFcmTokenObjects = function () {
  const created = this.createdAt || new Date();
  return (this.fcmTokens || []).map(t =>
    typeof t === 'string'
      ? { token: t, deviceId: null, platform: 'android', createdAt: created, lastActiveAt: new Date() }
      : t
  );
};

module.exports = mongoose.model('User', userSchema);