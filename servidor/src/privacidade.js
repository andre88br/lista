/**
 * Politica de privacidade. Fica no codigo, e nao num servico externo, porque a
 * ficha da loja precisa de um endereco publico e estavel - e porque o texto tem
 * que acompanhar o que o servidor realmente faz.
 */
export function paginaDePrivacidade() {
  return `<!doctype html>
<html lang="pt-BR">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Privacidade — Lista &amp; Estoque</title>
<style>
  :root { color-scheme: light dark; }
  body { font: 16px/1.65 system-ui, sans-serif; max-width: 42rem; margin: 0 auto;
         padding: 32px 20px 64px; background: #f6f7f6; color: #1a1c1a; }
  @media (prefers-color-scheme: dark) { body { background: #101410; color: #e6e8e6; } }
  h1 { font-size: 1.6rem; margin-bottom: 4px; }
  h2 { font-size: 1.1rem; margin-top: 32px; }
  .sub { opacity: .7; margin-top: 0; }
  ul { padding-left: 1.2rem; }
  code { background: rgba(127,127,127,.18); padding: 2px 6px; border-radius: 4px; }
</style>
<h1>Política de privacidade</h1>
<p class="sub">Aplicativo <strong>Lista &amp; Estoque</strong></p>

<h2>Quem opera este serviço</h2>
<p>Este aplicativo é pessoal e o servidor é mantido por seu próprio autor, numa
máquina particular. Não há empresa, publicidade nem compartilhamento de dados
com terceiros.</p>

<h2>O que é coletado</h2>
<p>Apenas o necessário para a lista funcionar entre dois ou mais aparelhos:</p>
<ul>
  <li><strong>Identificação da conta Google</strong>: identificador, nome e e-mail,
      recebidos no login. Servem para reconhecer você ao trocar de celular e para
      mostrar quem registrou cada leitura.</li>
  <li><strong>Produtos e quantidades</strong>: código de barras, nome do produto e
      as quantidades no estoque, na lista e nos comprados.</li>
  <li><strong>Histórico de leituras</strong>: cada leitura registrada, com data e
      autor, que é o que permite desfazer e sincronizar entre aparelhos.</li>
</ul>
<p>Não são coletados localização, contatos, fotos, telefone nem dados de pagamento.
O aplicativo usa a câmera <strong>somente</strong> para ler códigos de barras: as
imagens são processadas no próprio aparelho e nunca são enviadas nem armazenadas.</p>

<h2>Onde os dados ficam</h2>
<p>Num banco de dados no servidor do autor, acessível apenas por conexão
criptografada (HTTPS). O identificador de sessão de cada aparelho é guardado
apenas como resumo criptográfico, não em texto legível.</p>

<h2>Consulta a serviço externo</h2>
<p>Ao ler um código de barras desconhecido, o aplicativo consulta a base pública
<a href="https://world.openfoodfacts.org">Open Food Facts</a> para sugerir o nome
do produto. Só o código de barras é enviado, sem qualquer dado seu. Essa consulta
pode ser desligada nos Ajustes.</p>

<h2>Apagar seus dados</h2>
<p>No aplicativo: <strong>Ajustes → Compartilhar com outra pessoa → Excluir minha
conta</strong>. A exclusão é imediata e apaga sua identificação, seus aparelhos e
sua participação na casa.</p>
<p>Os produtos e o histórico da casa permanecem com os demais participantes, sem
vínculo com você — apagá-los sumiria com o estoque de quem continua usando. Se
você era a única pessoa da casa, nada mais resta lá.</p>
<p>Também é possível pedir a exclusão por e-mail ao autor do aplicativo, pelo
endereço informado na ficha da loja.</p>

<h2>Alterações</h2>
<p>Se esta política mudar, a versão nova passa a valer neste mesmo endereço.</p>
</html>`;
}
