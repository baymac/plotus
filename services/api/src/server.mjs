import { createApp } from './app.mjs';

const port = Number(process.env.PORT ?? 4000);
const server = createApp();
server.listen(port, '127.0.0.1', () => console.log(`Plotus API: http://localhost:${port}`));
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.close());
