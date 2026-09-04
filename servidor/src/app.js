import express from 'express';
import { autenticar, exigirCasa } from './auth.js';
import { criarAvisador } from './stream.js';
import { rotasDeCasas } from './rotas/casas.js';
import { rotasDeDispositivos } from './rotas/dispositivos.js';
import { rotasDeSync } from './rotas/sync.js';
import { ErroDeEntrada } from './validacao.js';

export function criarApp(pool) {
  const app = express();
  const avisador = criarAvisador();

  // Atras do Caddy: sem isso, o limitador enxergaria todo mundo como o mesmo IP.
  app.set('trust proxy', 1);
  app.disable('x-powered-by');
  app.use(express.json({ limit: '2mb' }));

  app.get('/saude', (_req, res) => res.json({ ok: true, servico: 'lista', agora: new Date().toISOString() }));

  // Quem abre o endereco no navegador merece uma resposta legivel, e nao o
  // JSON de rota nao encontrada. Serve tambem para conferir o servidor a olho.
  app.get('/', (_req, res) => {
    res.type('html').send(`<!doctype html>
<html lang="pt-BR">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Lista &amp; Estoque</title>
<style>
  :root { color-scheme: light dark; }
  body { font: 16px/1.6 system-ui, sans-serif; margin: 0; display: grid; place-items: center;
         min-height: 100vh; padding: 24px; background: #f6f7f6; color: #1a1c1a; }
  @media (prefers-color-scheme: dark) { body { background: #101410; color: #e6e8e6; } }
  main { max-width: 30rem; text-align: center; }
  .ok { display: inline-block; padding: 6px 14px; border-radius: 999px;
        background: #0F6B3F; color: #fff; font-weight: 600; font-size: 14px; }
  h1 { font-size: 1.4rem; margin: 18px 0 8px; }
  p { margin: 8px 0; opacity: .85; }
  code { background: rgba(127,127,127,.18); padding: 2px 6px; border-radius: 4px; }
</style>
<main>
  <span class="ok">Servidor no ar</span>
  <h1>Lista &amp; Estoque</h1>
  <p>Este endereço serve o aplicativo de lista de compras e estoque da casa. Não há nada para ver por aqui — quem conversa com ele é o app no celular.</p>
  <p>Para compartilhar a lista, abra o app em <strong>Ajustes → Compartilhar com outra pessoa</strong> e use o código da casa.</p>
  <p><code>/saude</code> responde o estado do serviço.</p>
</main>
</html>`);
  });

  app.use('/v1/dispositivos', rotasDeDispositivos(pool));

  // Daqui para baixo, tudo exige o token do aparelho.
  app.use('/v1', autenticar(pool));
  app.use('/v1/casas', rotasDeCasas(pool));
  app.use('/v1/sync', rotasDeSync(pool, avisador));

  // Aviso em tempo real: o outro celular sincroniza assim que algo muda.
  app.get('/v1/stream', exigirCasa, (req, res) => {
    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache, no-transform',
      Connection: 'keep-alive',
      'X-Accel-Buffering': 'no',
    });
    res.write('event: conectado\ndata: {}\n\n');

    const cancelar = avisador.inscrever(req.dispositivo.casaId, res);
    // Ping periodico para o proxy e o Android nao derrubarem a conexao ociosa.
    const ping = setInterval(() => res.write(': ping\n\n'), 25_000);
    req.on('close', () => {
      clearInterval(ping);
      cancelar();
    });
  });

  app.use((_req, res) => res.status(404).json({ erro: 'rota nao encontrada' }));

  app.use((erro, _req, res, _proximo) => {
    const status = erro instanceof ErroDeEntrada ? 400 : erro.status || 500;
    if (status >= 500) console.error('[erro]', erro);
    res.status(status).json({ erro: status >= 500 ? 'erro interno' : erro.message });
  });

  app.avisador = avisador;
  return app;
}
