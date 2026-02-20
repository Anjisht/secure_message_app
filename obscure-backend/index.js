// index.js

require('dotenv').config();
const express = require('express');
const axios = require('axios');
const mongoose = require('mongoose');
const http = require('http');
const { Server } = require("socket.io"); 

const helmet = require('helmet');
const cors = require('cors');
const morgan = require('morgan');
const rateLimit = require('express-rate-limit');

const userRoutes = require('./routes/userRoutes');
const roomRoutes = require('./routes/roomRoutes');
const messageRoutes = require('./routes/messageRoutes');
const mediaRoutes = require('./routes/mediaRoutes');
const { initChatSocket } = require('./sockets/chatSocket');


const app = express();
app.set('trust proxy', 1); 


const server = http.createServer(app);



// ====== SECURITY + BODY LIMIT ======
app.use(express.json({ limit: '100kb' }));
app.use(helmet());

// ====== CORS (reads from .env) ======
const allowed = (process.env.CORS_ORIGIN || '')
  .split(',')
  .map(s => s.trim())
  .filter(Boolean);

app.use(cors({
  origin: (origin, cb) => {
    if (!origin) return cb(null, true); // mobile apps/curl/postman
    return allowed.length === 0 || allowed.includes(origin)
      ? cb(null, true)
      : cb(new Error('Not allowed by CORS'));
  },
  credentials: true
}));

// ====== Logging in dev ======
if (process.env.NODE_ENV !== 'production') {
  app.use(morgan('dev'));
}

// ====== CONNECT MONGO ======
mongoose.connect(process.env.MONGO_URI)
  .then(() => console.log('✅ MongoDB connected'))
  .catch(err => {
    console.error('MongoDB connection error:', err);
    process.exit(1);
  });


// ====== RATE LIMIT AUTH ======
const authLimiter = rateLimit({ windowMs: 15 * 60 * 1000, max: 50 });
app.use('/api/users', authLimiter);

// ====== ROUTES ======
app.use('/api/users', userRoutes);
app.use('/api/rooms', roomRoutes);
app.use('/api/messages', messageRoutes);
app.use('/api/media', mediaRoutes);



const PYTHON_SERVICE_URL = process.env.PYTHON_SERVICE_URL || 'http://127.0.0.1:8000/api/v1/writer-prompt';
app.post('/api/proxy/writer-prompt', async (req, res) => {
  try {
    const response = await axios.post(PYTHON_SERVICE_URL, req.body);
    res.json(response.data);
  } catch (error) {
    console.error('Error proxying request:', error.message);
    res.status(error.response?.status || 500).json({
      message: 'Error communicating with the AI service.',
      details: error.message
    });
  }
});



// ====== SOCKET.IO ======
const io = new Server(server, {
  cors: {
    origin: allowed.length ? allowed : true,
    credentials: true
  }
});
initChatSocket(io);

// ====== HEALTH CHECK ======
app.get('/health', (_, res) => res.json({ ok: true }));


// ====== START ======
const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`🚀 Baatcheet backend running on http://localhost:${PORT}`);
});