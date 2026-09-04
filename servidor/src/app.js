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
