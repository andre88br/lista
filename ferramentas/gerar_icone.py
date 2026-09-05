"""Icone do app: carrinho colorido com o codigo de barras branco dentro do cesto.

Geometria do primeiro desenho, que foi o escolhido. Duas mudancas em cima dele:
as cores pedidas (carrinho ambar, barras brancas) e um encolhimento, porque no
original as rodas e a barra mais alta passavam da zona segura do icone
adaptativo e seriam cortadas em launchers de mascara redonda.
"""
import os
import cairosvg

AQUI = os.path.dirname(os.path.abspath(__file__))

FUNDO = "#0F6B3F"
BARRA = "#FFFFFF"
CARRINHO = "#FFC043"

BARRAS = [(38.0, 3.0), (43.0, 1.6), (46.6, 4.2), (53.0, 2.0), (57.0, 3.4),
          (62.6, 1.6), (66.4, 2.6), (71.2, 4.0), (77.4, 2.0)]
TOPO, FUNDO_BARRA = 24.0, 70.0

CESTO = "M34,42 L84,42 L77,70 L45,70 Z"
ALCA = "M18,28 L27,28 L34,42"
HASTES = "M48,70 L50,77 M75,70 L74,77"
RODAS = [(50.0, 82.0), (74.0, 82.0)]

# O desenho vai de ~16 a ~86 na horizontal e de 24 a 87 na vertical.
ESCALA = 0.86
TX = 54 - ESCALA * 51.0
TY = 54 - ESCALA * 55.5

def barras(y0, y1):
    return "".join(
        f'<rect x="{x}" y="{y0}" width="{w}" height="{y1 - y0}" fill="{BARRA}"/>'
        for x, w in BARRAS
    )

svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="432" height="432" viewBox="0 0 108 108">
  <defs><clipPath id="cesto"><path d="{CESTO}"/></clipPath></defs>
  <rect width="108" height="108" fill="{FUNDO}"/>
  <g transform="translate({TX:.2f},{TY:.2f}) scale({ESCALA})">
    <!-- Codigo de barras: livre acima do cesto, recortado dentro dele -->
    {barras(TOPO, 42)}
    <g clip-path="url(#cesto)">{barras(42, FUNDO_BARRA)}</g>

    <!-- Carrinho por cima; o contorno na cor do fundo separa das barras -->
    <path d="{CESTO}" fill="none" stroke="{FUNDO}" stroke-width="6.5" stroke-linejoin="round"/>
    <path d="{CESTO}" fill="none" stroke="{CARRINHO}" stroke-width="3.8" stroke-linejoin="round"/>
    <path d="{ALCA}" fill="none" stroke="{FUNDO}" stroke-width="6.5" stroke-linecap="round" stroke-linejoin="round"/>
    <path d="{ALCA}" fill="none" stroke="{CARRINHO}" stroke-width="3.8" stroke-linecap="round" stroke-linejoin="round"/>
    <path d="{HASTES}" fill="none" stroke="{CARRINHO}" stroke-width="3.8" stroke-linecap="round"/>
    {"".join(f'<circle cx="{x}" cy="{y}" r="5" fill="{CARRINHO}"/>' for x, y in RODAS)}
  </g>
</svg>'''

open(f"{AQUI}/final.svg", "w").write(svg)
for nome, tam in [("final.png", 432), ("final-48.png", 144)]:
    cairosvg.svg2png(bytestring=svg.encode(), write_to=f"{AQUI}/{nome}", output_width=tam, output_height=tam)
print("renderizado")

# ------------------------------------------------- vector drawable do Android
def ret(x, y, w, h):
    return f"M{x},{y}h{w}v{h}h{-w}z"

def circulo(cx, cy, r):
    return f"M{cx - r},{cy}a{r},{r} 0 1 0 {2 * r},0a{r},{r} 0 1 0 {-2 * r},0z"

def barras_xml(y0, y1, indent):
    return "".join(
        f'\n{indent}<path android:pathData="{ret(x, y0, w, y1 - y0)}" android:fillColor="{BARRA}"/>'
        for x, w in BARRAS
    )

frente = f'''<?xml version="1.0" encoding="utf-8"?>
<!--
  Carrinho de compras com o codigo de barras dentro do cesto.

  O grupo externo encolhe o desenho para dentro da zona segura do icone
  adaptativo: sem isso, as rodas e a barra mais alta seriam cortadas em
  launchers de mascara redonda.

  O contorno verde sob o contorno ambar e o que separa o carrinho das barras
  brancas que passam atras dele.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

  <group
      android:translateX="{TX:.2f}"
      android:translateY="{TY:.2f}"
      android:scaleX="{ESCALA}"
      android:scaleY="{ESCALA}">

    <!-- Codigo de barras acima do cesto -->{barras_xml(TOPO, 42, "    ")}

    <!-- Codigo de barras dentro do cesto, recortado por ele -->
    <group>
      <clip-path android:pathData="{CESTO}"/>{barras_xml(42, FUNDO_BARRA, "      ")}
    </group>

    <!-- Carrinho -->
    <path android:pathData="{CESTO}" android:strokeColor="{FUNDO}"
        android:strokeWidth="6.5" android:strokeLineJoin="round"/>
    <path android:pathData="{CESTO}" android:strokeColor="{CARRINHO}"
        android:strokeWidth="3.8" android:strokeLineJoin="round"/>

    <path android:pathData="{ALCA}" android:strokeColor="{FUNDO}"
        android:strokeWidth="6.5" android:strokeLineCap="round" android:strokeLineJoin="round"/>
    <path android:pathData="{ALCA}" android:strokeColor="{CARRINHO}"
        android:strokeWidth="3.8" android:strokeLineCap="round" android:strokeLineJoin="round"/>

    <path android:pathData="{HASTES}" android:strokeColor="{CARRINHO}"
        android:strokeWidth="3.8" android:strokeLineCap="round"/>
{"".join(f'    <path android:pathData="{circulo(x, y, 5)}" android:fillColor="{CARRINHO}"/>' + chr(10) for x, y in RODAS)}  </group>
</vector>
'''

fundo_xml = f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
  <path android:pathData="M0,0h108v108h-108z" android:fillColor="{FUNDO}"/>
</vector>
'''

open(f"{AQUI}/ic_launcher_foreground.xml", "w").write(frente)
open(f"{AQUI}/ic_launcher_background.xml", "w").write(fundo_xml)
print("vector drawable gerado")
