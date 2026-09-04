import { criarApp } from './app.js';
import { criarPool, migrar } from './db.js';

const porta = Number(process.env.PORT || 8080);
const pool = criarPool();

await migrar(pool);

const app = criarApp(pool);
const servidor = app.listen(porta, () => {
  console.log(`[lista] servidor ouvindo na porta ${porta}`);
});

for (const sinal of ['SIGTERM', 'SIGINT']) {
  process.on(sinal, () => {
    console.log(`[lista] recebido ${sinal}, encerrando`);
    servidor.close(async () => {
      await pool.end();
      process.exit(0);
    });
  });
}
