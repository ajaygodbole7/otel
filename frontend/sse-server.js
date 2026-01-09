const express = require("express");
const cors = require("cors");
const app = express();

app.use(
  cors({
    origin: "http://localhost:5173",
    credentials: true,
    methods: ["GET", "OPTIONS"],
    allowedHeaders: ["Content-Type", "Authorization"],
  })
);

app.get("/events", (req, res) => {
  res.setHeader("Content-Type", "text/event-stream");
  res.setHeader("Cache-Control", "no-cache");
  res.setHeader("Connection", "keep-alive");

  if (typeof res.flushHeaders === "function") {
    res.flushHeaders();
  }
  res.write(": connected\n\n");

  const heartbeat = setInterval(() => {
    res.write(": heartbeat\n\n");
  }, 25_000); // commonly 15–30s

  const ticker = setInterval(() => {
    const payload = JSON.stringify({ time: new Date().toISOString() });
    res.write(`event: notification\n`);
    res.write(`data: ${payload}\n\n`);
  }, 1000);

  req.on("close", () => {
    clearInterval(ticker);
    clearInterval(heartbeat);
    res.end();
  });
});

app.listen(4000, () => console.log("SSE server running on port 4000"));
