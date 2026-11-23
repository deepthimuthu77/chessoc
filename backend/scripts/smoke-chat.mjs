import assert from 'node:assert/strict';

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const results = {};
async function api(path, body, token) {
  const response = await fetch('http://localhost/api' + path, { method: 'POST', headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: JSON.stringify(body), signal: AbortSignal.timeout(10000) });
  assert(response.ok, path + ': ' + response.status);
  return response.json();
}
async function until(check) { const end = Date.now() + 10000; while (!check()) { assert(Date.now() < end, 'delivery timeout'); await sleep(15); } }
async function connect(room, guest) {
  const joined = await api('/join', { room }, guest.token);
  const { ticket } = await api('/ticket', {}, guest.token);
  const ws = new WebSocket('ws://localhost/game?gameId=' + joined.gameId + '&ticket=' + encodeURIComponent(ticket));
  const client = { ws, joined, messages: [], events: [], snapshot: null, caps: [] };
  ws.addEventListener('message', event => { const env = JSON.parse(String(event.data)); client.events.push(env); if (env.type === 'chat') client.messages.push(env.payload); if (env.payload?.snapshot) { client.snapshot = env.payload.snapshot; client.caps = env.payload.capabilities ?? []; } });
  await until(() => client.snapshot);
  return client;
}
function send(client, text, extra = {}) { client.ws.send(JSON.stringify({ v: [1, 0], type: 'chat', payload: { text, ...extra } })); }
const guests = await Promise.all(Array.from({ length: 5 }, () => api('/login', {})));
const clients = [];
const rooms = [];
try {
  for (const [index, variant] of ['standard', 'crazyhouse'].entries()) {
    const { gameId } = await api('/play/create', { variant, minutes: 30, increment: 0, rated: false, vsEngine: false }, guests[index * 2].token);
    const group = [];
    for (const guest of [guests[index * 2], guests[index * 2 + 1], guests[4]]) { const client = await connect(gameId, guest); group.push(client); clients.push(client); }
    rooms.push({ gameId, group });
  }
  for (const [index, room] of rooms.entries()) {
    const text = 'QA room isolation ' + index;
    send(room.group[0], text);
    await until(() => room.group.every(client => client.messages.some(message => message.text === text)));
  }
  await sleep(300);
  results.matchIsolation = rooms.every((room, index) => room.group.every(client => !client.messages.some(message => message.text === 'QA room isolation ' + (1 - index))));
  assert(results.matchIsolation);
  const sender = rooms[0].group[0];
  const receiver = rooms[0].group[1];
  async function roundtrip(text) {
    await sleep(1100);
    const count = receiver.messages.length;
    send(sender, text);
    await until(() => receiver.messages.length > count);
    return receiver.messages.at(-1);
  }
  for (const text of ['fuck', 'SHIT', 'f.u.c.k', 'sh1t', 'f\u200buck', 'ｆｕｃｋ']) {
    const message = await roundtrip(text);
    (results.profanity ??= []).push({ masked: message.text.includes('*') && message.text !== text });
  }
  results.safeWordsUnchanged = (await roundtrip('classical assessment Scunthorpe good game')).text === 'classical assessment Scunthorpe good game';
  const emoji = 'Good game 😀🔥👍🏽👨‍👩‍👧‍👦🇮🇳 — नमस्ते বাংলা';
  results.emojiRoundtrip = (await roundtrip(emoji)).text === emoji;
  const boundary = (await roundtrip('x'.repeat(499) + '😀tail')).text;
  results.emojiBoundarySafe = boundary === 'x'.repeat(499);
  results.lengthLimited = (await roundtrip('x'.repeat(2048))).text.length <= 500;
  results.controlsRemoved = (await roundtrip('hello\u202Eworld\u0007')).text === 'helloworld';
  const html = '<img src=x onerror="alert(1)"><script>alert(1)</script>';
  results.htmlDeliveredAsText = (await roundtrip(html)).text === html;
  await sleep(1100);
  const spoofCount = receiver.messages.length;
  send(sender, 'QA sender check', { color: 'black', playerId: guests[1].account?.id });
  await until(() => receiver.messages.length > spoofCount || sender.events.at(-1)?.type === 'error');
  results.senderNotSpoofed = receiver.messages.slice(spoofCount).every(message => message.color === 'white');
  const emptyCount = receiver.messages.length;
  await sleep(1100);
  send(sender, '  \t\n  ');
  await sleep(250);
  results.emptyIgnored = receiver.messages.length === emptyCount;
  const errorCount = sender.events.filter(event => event.type === 'error').length;
  sender.ws.send(JSON.stringify({ v: [1, 0], type: 'chat', payload: { text: { invalid: true } } }));
  await until(() => sender.events.filter(event => event.type === 'error').length > errorCount);
  sender.ws.send(JSON.stringify({ v: [1, 0], type: 'ping', payload: {} }));
  await until(() => sender.events.at(-1)?.type === 'pong');
  results.malformedRecoverable = true;
  await sleep(1100);
  const floodCount = receiver.messages.length;
  for (let i = 0; i < 20; i++) send(sender, 'QA burst ' + i);
  await sleep(400);
  results.burstDelivered = receiver.messages.length - floodCount;
  results.spamLimited = results.burstDelivered > 0 && results.burstDelivered < 20;
  assert(results.safeWordsUnchanged && results.emojiRoundtrip && results.lengthLimited && results.senderNotSpoofed && results.emptyIgnored && results.htmlDeliveredAsText);
  assert(results.profanity.every(item => item.masked) && results.emojiBoundarySafe && results.controlsRemoved && results.spamLimited);
  console.log(JSON.stringify(results, null, 2));
} finally {
  for (const room of rooms) {
    const first = room.group[0];
    const cap = first.caps.find(item => item.kind === 'abort');
    if (cap && !first.snapshot.result) {
      first.ws.send(JSON.stringify({ v: [1, 0], type: 'intent', payload: { intentId: crypto.randomUUID(), capabilityId: cap.id, params: {}, clientTimestamp: Date.now() } }));
      await until(() => first.snapshot.result);
    }
  }
  clients.forEach(client => client.ws.close());
}

