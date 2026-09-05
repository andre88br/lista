# Ferramentas

## `gerar_icone.py`

Gera o ícone do app: a prévia em PNG (para olhar) e os `vector drawable` que
vão para `app/src/main/res/drawable/`, tudo a partir da mesma geometria — assim
o que se vê na prévia é exatamente o que o Android desenha.

```bash
pip install cairosvg
python3 ferramentas/gerar_icone.py
cp ic_launcher_*.xml app/src/main/res/drawable/
```

Para mudar o ícone, edite a geometria e as cores no topo do script, não o XML
gerado.

Duas coisas que o desenho precisa respeitar, e que o script já cuida:

- **Zona segura.** O ícone adaptativo tem 108 unidades, mas só as 66 centrais
  aparecem garantidamente: launchers de máscara redonda cortam o resto. Por isso
  o desenho é encolhido por um grupo com `scale`.
- **Sem desfoque.** O formato de vetor do Android aceita gradiente, mas não
  aceita sombra desfocada. Volume, quando houver, tem que vir de gradiente e
  formas de borda dura.
