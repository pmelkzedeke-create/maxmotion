# MaxMotion — plano com 100 ideias

Estúdio nativo Android de motion graphics. Este plano parte do que **já existe** (camadas, Bézier, expressões, shaders GLSL, pré-comp, LUT, rampa de velocidade, rastreador 2D, recorte IA, export MP4) e aponta o que falta para o app virar o “After Effects de bolso” sem perder o feeling CapCut.

**Norte:** um criador no Recife, em Belém ou em São Paulo consegue, só no celular, fazer intro cinética, lower-third, reel 9:16 e bumper de marca — com precisão de keyframe, não só presets.

**Como usar:** cada ideia tem prioridade  
`P0` = 90 dias · `P1` = 6 meses · `P2` = 12–18 meses · `P3` = visão

**Status no código (não são as 100):** Fase A P0 (1, 2, 4, 6, 9, 83, 84, 91, 93, 94), templates/marca (73–75) e type/fontes (25, 26) estão no app. O resto continua backlog.

---

## Faseamento

| Fase | Quando | Foco | Ideias âncora |
|---|---|---|---|
| **A — Afiar o estúdio** | em curso no código | Precisão, estabilidade, templates demo, texto, export confiável | 1, 2, 4, 6, 9, 83, 84, 91, 93, 94 **já no app** |
| **B — Virar produto** | 25–26 e 73–75 já no app; resto 3–6 meses | Packs, áudio no beat, presets de animação, loja de curvas | 13–24, 47–54, 73–78 |
| **C — IA que entrega** | 6–12 meses | Prompt → composição, legendas, recorte, auto-edit no estilo MaxMotion | 55–64 |
| **D — Ecossistema** | 12–18 meses | Comunidade BR, collab, plugin de shader, Windows/tablet | 73–82, 96–100 |

---

## Já existe (não reinventar)

Camadas (vídeo, imagem, vetor, texto, nulo, câmera 3D, luz, ajuste, pré-comp, áudio) · 27 blend modes · keyframes Bézier · graph editor · pick whip · expressões · trim path · texto por caractere · máscaras · 22 efeitos GLSL · color grading + .cube LUT · speed ramp · freeze frame · motion tracker 2D · recorte ML Kit · legendas offline · undo/redo · JSON do projeto · export H.264.

As 100 ideias abaixo **não** são essas features. São o que falta em volta delas.

---

## 1. Núcleo do estúdio — precisão no celular
*Fazer o editor parecer After Effects, com o polegar.*

1. **`P0` ✅ Ripple edit na timeline** — ao encurtar um clipe, o restante da composição empurra; essencial para cortes rápidos sem bagunçar keyframes.
2. **`P0` ✅ Slip / slide de clipe** — deslizar o conteúdo interno do vídeo sem mover o bloco na timeline ( CapCut faz, AE faz, nós ainda não).
3. **`P0` Snap inteligente com tipos** — ímã separado para playhead, beats, keyframes, in/out e marcadores; o usuário escolhe o que cola.
4. **`P0` ✅ Work area (barra de trabalho)** — renderizar / loopar só o trecho B–N, não os 8 s inteiros. Acelera preview e export de teste.
5. **`P1` Markers nomeados com cor** — “drop”, “logo”, “CTA”; pulo por marker no teclado Bluetooth e no dock.
6. **`P0` ✅ Timecode tap-to-edit** — toque no `00:01:12` e digite o frame; motion designers vivem disso.
7. **`P1` Multi-seleção retangular na timeline** — arrastar uma caixa em keyframes/clipes, não só tap+multi.
8. **`P1` Copiar atributos** — “colar só posição + opacidade nesta camada”, estilo AE Paste Attributes.
9. **`P0` ✅ Autosave a cada 15 s + versões** — crash no Android é realidade; histórico de 10 snapshots por projeto.
10. **`P1` Composições aninhadas ilimitadas com breadcrumb** — `Principal › Intro › Logo 3D`, toque para subir um nível.
11. **`P1` Painel de projeto (bin)** — assets, comps, pastas, preview de waveform/filmstrip, igual Project panel do AE.
12. **`P2` Atalhos Bluetooth / tablet com teclado** — JKL, I/O, U (revelar keyframes), F (fit), Space, Ctrl+D. Tablet vira mesa de edição.

---

## 2. Motion graphics & vetor
*O diferencial contra CapCut. Dobrar nisso.*

13. **`P0` Biblioteca de 40 shapes animáveis** — setas, bursts, lines, grids, lower-thirds vazios, não só retângulo/círculo/estrela.
14. **`P0` Trim path com offset + múltiplos paths** — já temos trim; falta offset contínuo e vários contours no mesmo layer.
15. **`P1` Repeater (AE) nativo** — copiar um vetor em anel/linha com offset de rotação e delay de trim. Intro cinética explode com isso.
16. **`P1` Offset Paths / Round Corners como efeito de forma** — canto vivo vira canto arredondado sem redesenhar.
17. **`P1` Fill/Stroke independente por path** — um objeto, dois strokes (neon + core branco), clássico de logo reveal.
18. **`P0` Gradiente animável (posição, ângulo, stops)** — hoje o gradiente é estático; motion de cor é 50% do look neon.
19. **`P1` Noise / grain vetorial + wiggle paths** — linhas “vivas” sem shader pesado.
20. **`P1` Boolean de formas** — união, subtração, interseção entre dois vetores (logo marks).
21. **`P2` Import SVG / Lottie / Rive** — o mercado BR de motion já entrega Lottie; importar é onboarding de clientes de agência.
22. **`P2` Export Lottie / Rive / SVG animado** — o inverso: o celular vira ferramenta de entrega para app/web.
23. **`P1` Stroke dashed + gap animável + end caps custom** — setas que “correm” no path.
24. **`P2` Motor de física 2D opcional** — gravidade, bounce, colisão leve em nulos; ótimo para kinetic type “caindo”.

---

## 3. Texto cinético
*Reels vivem de letra. Hoje o range por caractere é o começo.*

25. **`P0` ✅ Presets de type: 30 looks** — pop palavra, scramble, typewriter, split flip, blur-in, tracking explode. Um tap.
26. **`P0` ✅ Font picker** — 3 bundled + looks de sistema (Oswald/Playfair/Script…) + TTF do usuário. Google Fonts CDN ainda não.
27. **`P1` Text animator completo** — selector (range, wiggly, random) + propriedades (offset, scale, skew, fill, tracking) empilháveis.
28. **`P1` Numerais e contadores** — `0 → 1.2M` com easing, para infográfico e “número da live”.
29. **`P1` Path text** — texto em círculo / curva Bézier, com offset no tempo.
30. **`P1` Caixa de texto com auto-break e box resize** — lower-third que não estoura em 9:16.
31. **`P2` Variable fonts (peso animável)** — interpolar wght 200→900 no beat.
32. **`P1` Emoji / sticker pack vetorial** — reels BR usam; manter no mesmo motor de shape.
33. **`P2` OpenType features** — ligatures, small caps, números tabulares no JetBrains Mono (já é a fonte de UI).
34. **`P1` Estilos de texto salvos no projeto** — “Título marca”, “CTA”, “Tag”; muda um, atualiza todos.

---

## 4. Vídeo, cor e VFX
*Do recorte ao look de clipe.*

35. **`P0` Chroma key com spill matte visual** — o efeito existe; falta preview do matte (alpha) e descontaminate de verde.
36. **`P1` Curves RGB + luma com pontos arrastáveis** — o inspector de cor hoje é lift/gamma/gain; curves é o que coloristas pedem.
37. **`P1` Scopes: waveform, parade, vectorscope** — minúsculos, OLED, no inspector. Diferencial hardcore.
38. **`P1` Match color / shot matching** — um clipe referencia o look do outro (LUT automático).
39. **`P0` Biblioteca de 20 LUTs cinema** — Teal-Orange, Night Neon, Rec709→cine, BW punch. Import .cube já existe.
40. **`P1` Secondary color (qualificação HSL)** — isolar um vermelho da camisa e saturar só ele.
41. **`P1` Glow / bloom empilhável com threshold visual** — Deep Glow já existe; falta preview do mask de brilho.
42. **`P2` Particle system 2D** — sparks, dust, embers, confetti no beat. Reels de festa e game.
43. **`P2` Light leak / film burn / analog pack** — overlays gerados, não só PNG.
44. **`P1` Stabilization 1-tap no tracker** — o rastreador 2D já está lá; expor “estabilizar camada” como botão único.
45. **`P2` Planar tracker (cantos de celular na mão)** — corner pin em telas, outdoors, camisetas.
46. **`P1` Adjustment layer com máscara de vinheta invertida** — grade só nas bordas, look de clipe pronto.

---

## 5. Áudio & ritmo
*Motion no celular morre sem beat.*

47. **`P0` Auto-beat markers mais visíveis + “animar no beat”** — um botão que espalha keyframes nos beats detectados.
48. **`P0` Ducking automático** — voz sobe, música desce; essencial para reels falados.
49. **`P1` Biblioteca de 80 SFX CC0** — whoosh, hit, glitch, sub drop, riser. Pack “MaxMotion Hits”.
50. **`P1` Waveform nas faixas com clip gain** — já há analyzer; desenhar e arrastar volume por segmento.
51. **`P1` Audio spectrum → expressão** — `thisComp.audioLevel` ligando glow/scale no baixo. O motor de expressão já aguenta.
52. **`P2` Stem separator on-device** — vocal / beat / bass (modelo leve) para remixar o próprio áudio do reel.
53. **`P1` Metronomo + grid de BPM editável** — o usuário corrige o detector quando a música é suja.
54. **`P2` MIDI / pad Bluetooth** — trigger de marker e playhead para quem edita com fone gamer.

---

## 6. IA (Gemini + on-device)
*IA que gera **composição**, não só texto.*

55. **`P1` Prompt → projeto** — “intro 3 s, logo no centro, anel neon, 9:16, 60 fps” gera layers + keyframes + expressão `time * 28`.
56. **`P1` Prompt → efeito** — “deixa mais VHS e um glow no título” empilha VHS_SCANLINES + DEEP_GLOW com params.
57. **`P0` Auto-captions com estilo MaxMotion** — legendas já existem offline; aplicar animator (pop por palavra) automático.
58. **`P1` Recorte pessoa + borda neon** — rotoscoper ML Kit já roda; preset “outline cyan 8 px + glow”.
59. **`P1` Remove fundo de produto** — além de pessoa: lata, tênis, celular. E-commerce de reel.
60. **`P2` Auto-edit: 15 s a partir de um vídeo longo** — cortes no beat, jump cuts, texto de hook. O “CapCut mágico”, mas com layers reais.
61. **`P2` Traduzir / reescrever texto on-canvas** — “deixa mais agressivo”, “versão PT-BR gíria”.
62. **`P1` Sugestão de curva Bézier** — “essa interpolação está linear demais”; Gemini devolve um bezier e aplica no graph editor.
63. **`P2` Shader a partir de descrição** — “distorção de calor no asfalto” gera GLSL sandbox (com sandbox de segurança).
64. **`P3` Agente de direção** — assiste o preview, aponta “título entra tarde, glow estoura no branco” e aplica o patch.

---

## 7. 3D, câmera e shaders
*Câmera 3D e órbita já existem. Falta mundo.*

65. **`P1` Grid 3D no chão + eixos RGB** — sem isso, orbitar é chute.
66. **`P1` Extrude de texto/vetor (fake 3D)** — profundidade, bevel, material emissivo. Look de intro 2012–2025.
67. **`P2` Import GLB simples** — um logo 3D do cliente, orbit camera, light. Sem virar Blender.
68. **`P1` Camera shake presets** — handheld, impact, rumble, tied ao S_Shake que já temos.
69. **`P1` Depth of field visual** — `dofBlur` já está no CameraSpec; precisa de preview com rack focus no playhead.
70. **`P2` Shader graph visual** — nós (noise, gradient, UV) compilados para o ShaderLibrary. Usuário avançado, marketplace depois.
71. **`P2` Environment map / reflexo fake** — matte chrome no prisma cinético da demo.
72. **`P3` Multi-câmera na mesma comp** — cortar entre Cam A / Cam B como live switcher.

---

## 8. Templates, assets e comunidade
*Isso vende o app. O motor sozinho não.*

73. **`P0` ✅ 12 templates oficiais 9:16** — intro neon, lower-third news, podcast cover, “cheguei”, black friday, igreja, futebol, açaí, loja de rua, DJ, faculdade, prefeitura.
74. **`P0` ✅ 12 templates 16:9 / 1:1** — bumper YouTube, end slate, post feed, thumbnail cinético.
75. **`P1` ✅ Replace logo / cor / fonte em 1 tap** — “trocar a marca” sem quebrar keyframes (essential graphics).
76. **`P1` Loja de curvas Bézier** — a biblioteca de curvas salvas já existe no Room; falta UI de pack “bounce”, “anticipation”, “overshoot”.
77. **`P1` Packs pagos de quem cria** — 70/30 para o motion designer BR. Alight Motion cresceu assim.
78. **`P2` Publicar template a partir do próprio projeto** — sanitiza assets, gera preview MP4 3 s, manda pra vitrine.
79. **`P1` Moodboards / “looks”** — um tap aplica LUT + grain + vinheta + tracking de título.
80. **`P2` Remix com crédito** — abrir template de outro, editar, republicar; watermark de autoria no JSON.
81. **`P2` Desafio semanal** — “essa curva + essa paleta”; feed in-app. Cresce TikTok do produto.
82. **`P3` Hub de jobs** — “preciso de intro 5 s, R$ 80”; o app vira praça de motion freelancer do Norte/Nordeste também, não só SP.

---

## 9. UX mobile, onboarding, acessibilidade

83. **`P0` ✅ Tutorial da demo em 45 s** — a *Intro Cinética Neon* já é o melhor onboarding; overlay “arraste o playhead / toque no prisma / abra Curvas”.
84. **`P0` ✅ Empty states com ação** — “nenhuma camada: importe um vídeo ou toque em Forma”. Hoje o hub já é bom; o editor vazio não.
85. **`P1` Modo uma mão** — dock compacto, canvas maior, timeline em drawer. Ônibus, fila, moto-taxi.
86. **`P1` Gestos: dois dedos undo, três redo** — o callback já existe no CanvasViewport; ensinar com hint na primeira sessão.
87. **`P1` Haptics no snap e no beat** — o polegar “sente” o ímã.
88. **`P1` Modo daltônico / contraste de tracks** — as cores de layer (azul, rosa, roxo) precisam de padrão + ícone, não só hue.
89. **`P2` TalkBack nos controles críticos** — play, exportar, desfazer, nome da camada.
90. **`P1` Performance HUD opcional** — ms/frame, GPU, resolução de preview. O botão 1/1 · 1/2 · 1/4 já está no header; mostrar *por que* está lento.
91. **`P0` ✅ Recuperar projeto após kill** — banner “restauramos Intro Cinética Neon de 14:32”.
92. **`P2` Layout tablet 80/20** — canvas grande + inspector permanente à direita (o EditorScreen já bifurca em `maxWidth > 720.dp`).

---

## 10. Export, produto e Brasil

93. **`P0` ✅ Export previsível** — fila, estimativa de MB (já calculamos), cancelar, abrir no WhatsApp / Reels / chooser. Drive ainda não tem botão próprio.
94. **`P0` ✅ Presets de entrega** — Reels 1080×1920 30/60, YouTube 1080p, Shorts, WhatsApp compacto, “cliente agência ProRes-like” (H.264 high bitrate).
95. **`P1` Alpha / WebM / PNG sequence** — overlay para Streamers e obs; o motor de layer já tem opacidade.
96. **`P1` Cloud backup opcional (Drive)** — o conector existe; “salvar projeto .maxmotion no Meu Drive”. JSON + assets.
97. **`P1` Modelo de negócio claro** — Free (720p, marca d’água suave) · Pro mensal (4K, sem marca, LUTs) · Studio anual (templates + loja). Preço em R$, Pix.
98. **`P2` Modo offline honesto** — IA Gemini some, mas recorte ML Kit, legendas offline, shaders e export continuam. Badge “tudo local”.
99. **`P2` Página de status + telemetria opt-in** — crashes de GL, tempo de export, quais efeitos matam mid-range (Helio G85, Snapdragon 680). O público-alvo no Pará/Nordeste não é flagship.
100. **`P3` Companion desktop / web preview do JSON** — o projeto já é um documento. Um player web do `.maxmotion` é o próximo “abra uma prévia”, de verdade, sem fake HTML.

---

## Ordem sugerida dos próximos 10 sprints

1. Autosave + work area + ripple + slip (1, 2, 4, 9, 91)  
2. Tutorial da demo + empty states (83, 84)  
3. Export para WhatsApp/Reels + presets de entrega (93, 94)  
4. 24 templates oficiais com “trocar marca” (73, 74, 75)  
5. 30 presets de type + picker de fonte (25, 26) **já no app**  
6. Animar no beat + ducking + pack SFX (47, 48, 49)  
7. Repeater + trim offset + gradiente animável (14, 15, 18)  
8. Captions com estilo + recorte com borda neon (57, 58)  
9. Loja de curvas + essential graphics (76, 75)  
10. Prompt → projeto (55)

---

## O que *não* fazer agora

- Virar editor de filme longo (CapCut/VN já ganharam isso).  
- Virar Blender. GLB é import, não modelagem.  
- Marketplace antes de 24 templates internos — senão a vitrine nasce vazia.  
- IA que devolve MP4 opaco. A IA do MaxMotion tem que devolver **layers**.

---

*Documento vivo. As 100 ideias cabem no motor que já está no repo; a aposta é produto, não reescrever o renderer.*
