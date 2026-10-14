# Guia do OptXbox — usar num port novo

Repositório: https://github.com/dfdx047/Optxbox (versão 0.2.0, 2026-10-14).
Este guia é para colar no projeto de um port novo de Xbox 360 → Android, junto com o
`GUIA_PORT_XBOX360_ANDROID.md`. Diz o que é a ferramenta, o que o port novo precisa ter para
ela funcionar, e como fica cada rodada de testes entre o dfdx047 e o Claude.

---

## 1. O que é

CLI em Python 3 (sem dependências) que faz em segundos o que no DBZ3 foi feito à mão em mais de
50 rodadas de logs: lê os logs e as capturas de frame que o runtime do port escreve, encontra os
padrões conhecidos que custam GPU e diz o que ligar, por ordem de ganho.

No DBZ3 (Budokai 3 HD) o caminho foi: Snapdragon 865 de ~37 para 60 FPS cravados, Mali-G57 de
13 para 22–24 FPS — quase tudo cortando **fins de passe** em GPUs tiler (cada fim de passe grava
e recarrega a cor + profundidade de 1280x720 inteiras).

A ferramenta **não substitui os logs**: eles são a medição. Ela só transforma um zip de logs num
resumo curto, para não ter de anexar zips nem ler tudo à mão.

---

## 2. Instalação (no PC, uma vez)

```
cd ~ && git clone https://github.com/dfdx047/Optxbox.git
```

Atualizar mais tarde:

```
cd ~/Optxbox && git pull
```

Roda com `python3 -m optxbox …` de dentro da pasta (não precisa de instalar com pip).

---

## 3. O que o port novo precisa ter (contrato de instrumentação)

A ferramenta não fala com o jogo: lê o que o runtime escreve. A implementação de referência são
os patches do DBZ3-Android ao ReXGlue SDK v0.10 e o launcher Dragon Fury. Num port novo baseado
no mesmo SDK, os patches são reaproveitados quase sem mudanças.

| O quê | Para quê | Referência no DBZ3-Android |
|---|---|---|
| Linha `perf` a cada 5 s no log (fps, GPU ms por categoria, passes, resolves, draws) | desempenho por aparelho | patches 0016/0017/0018/0022 |
| Linha `fins de passe N/f` com a causa de cada fim de passe | saber o que parte os passes | patches 0033/0043 |
| Captura de 2 frames `.dfc` (registos, draws, resolves, shaders, memória) | ver o frame e os padrões | patch 0027 (botão "Capturar 2 frames") |
| Tempo de GPU por draw na captura (`.dfc.timing.txt`) | que shaders pesam | patch 0034 |
| Linha `rt_direct:` (resolves eliminados, restauros ignorados) | confirmar a eliminação | patch 0032 |
| Botão **Exportar logs** no launcher → zip em `Download/` | levar tudo para o PC | `data/LogExporter.kt` |

Formato do zip exportado (o `report` procura estes nomes):

```
DragonFury-logs-AAAAMMDD-HHMMSS.zip   (num port novo: <App>-logs-....zip)
├── aparelho.txt              modelo, Android, SoC, driver ativo
├── cvars.txt                 overrides manuais (pode estar vazio)
├── logcat.txt                logcat da app (formato threadtime)
└── logs/
    ├── <app>_last.log        log da sessão
    └── capture/*.dfc (+ .dfc.timing.txt)
```

As linhas do runtime usam o prefixo `dbz3: `. Num port novo, ou se mantém o prefixo, ou se passa
`--prefix "<novo>: "` aos comandos. O nome do zip no aparelho muda com `--adb-pattern`.

O formato binário da captura e as linhas de log estão descritos no README do repositório
(secção "Contrato de instrumentação").

---

## 4. Uma rodada de testes

### No aparelho
1. Instalar a build nova.
2. Jogar a cena de referência (no DBZ3: uma luta de 30–40 s; num port novo, a cena mais pesada
   e um menu).
3. Se for a primeira rodada do jogo ou se algo mudou muito: no menu do jogo, **Capturar 2
   frames** durante a cena pesada.
4. **Exportar logs** no launcher.

### No PC (aparelhos ligados por USB)

```
cd ~/Optxbox && python3 -m optxbox report --adb
```

O comando:
- puxa por adb o zip mais recente de cada aparelho ligado para `optxbox-logs/<serial>/`;
- resume cada aparelho em algumas dezenas de linhas;
- compara com a rodada anterior guardada na mesma pasta (✓ melhorou, ✗ piorou).

### Para o Claude
Colar o texto do `report` na conversa, com uma frase sobre o que se viu na tela (travou, piscou,
ficou preto, controles…). Os zips completos só quando o Claude pedir — normalmente quando o
resumo não explica um problema visual ou um crash.

### Variações
- Zips já no PC: `python3 -m optxbox report zip1.zip zip2.zip`
- Comparar com uma rodada escolhida: `python3 -m optxbox report novo.zip --previous antigo.zip`
- Só um aparelho: `python3 -m optxbox report --adb --serial 611e0fa0`
- Mais rápido, sem analisar capturas: `--no-capture`
- Port com outro prefixo/nome de zip: `--prefix "rb2: " --adb-pattern "RB2-logs-*.zip"`

---

## 5. O que o resumo mostra

Por aparelho:
- aparelho, SoC, GPU e versão Vulkan, driver, `cvars.txt` em uso;
- **lutas** e **menus** separados (janelas com ≥ 200 draws/frame contam como cena pesada;
  `--fight-draws N` para mudar): fps (média e p5), GPU ms por frame (média e p95), draws,
  resolves e fins de passe por frame;
- tempo de GPU por categoria: desenho, transfer, resolve, texturas, swap;
- causas dos fins de passe (upload, barreira de imagem por destino, mudança de render target);
- eliminação de resolves/restauros (`rt_direct`);
- erros e avisos únicos do log (agrupados, com contagem), linhas raras do runtime e erros E/F do
  logcat;
- se o zip tiver captura: draws, resolves, passes e trocas de RT por frame, e os padrões
  encontrados com o interruptor de cada um.

Como ler as categorias:
- **desenho** alto → shaders pesados ou muitos fins de passe a meio das cenas;
- **resolve** alto → cada cópia da tela fecha o passe (num tiler isso é o custo, não a cópia);
- **transfer** alto → cópias entre configurações de render target que partilham EDRAM (ou um
  `Clear()` 4x que depois tem de ir para 1x);
- **texturas** alto → cargas/descompressão de texturas a meio do jogo.

---

## 6. Comandos para análise mais funda

| Comando | O que faz |
|---|---|
| `python3 -m optxbox analyze captura.dfc` | padrões encontrados no frame, ms estimados, interruptores sugeridos |
| `python3 -m optxbox timeline captura.dfc [--draws]` | a sequência de passes, resolves e quem lê cada resolve |
| `python3 -m optxbox perf <app>_last.log` | resumo de um log (com `--min-draws`/`--max-draws`) |
| `python3 -m optxbox compare antes.log depois.log` | diferenças entre duas rodadas |
| `python3 -m optxbox cvars captura.dfc` | bloco de `cvars.txt` sugerido |
| `python3 -m optxbox shaders captura.dfc --dump pasta/` | extrai (e desmonta, com `xdisasm`) os shaders |
| `python3 -m optxbox d3dmap --generated <generated/>` | mapa das funções D3D do XDK no código gerado (para hooks) |

Todos aceitam `--json` e `-o arquivo`.

---

## 7. Padrões que a ferramenta já reconhece (do DBZ3)

| Padrão | O que é | Interruptor no DBZ3 |
|---|---|---|
| A | `Clear()` do XDK como retângulo 4x MSAA de meia largura (VS 1E6883FCCDE1F688 / PS A4A965C189287B99) | `dbz3_clear_to_1x` |
| B | profundidade restaurada desenhando-a como cor na base da profundidade | `dbz3_skip_depth_restore` |
| C | cópia da cor + desenho de volta idêntico (verificado pelos vértices) | `dbz3_rt_elide_restores` |
| D | resolves sem leitor / lidos só por restauros | `dbz3_rt_elide_restores` |
| rt_aliasing | render targets que partilham EDRAM (transferências do Xenia) | `dbz3_rt_direct` |
| resolve_cost | custo base das cópias da tela | `dbz3_direct_resolve_texture`, `dbz3_resolve_early_transition` |
| efeitos | DOF, FXAA, bloom (cadeia de resolves a descer de tamanho) | `dbz3_fx_dof/fxaa/bloom` (opcionais) |
| shaders pesados | passes de tela inteira com muitas leituras de textura | `dbz3_spec_texture_signs`, `dbz3_tex_implicit_lod` |

Os hashes do Clear vêm do XDK: devem repetir-se em jogos com a mesma versão do D3D (por
confirmar no próximo jogo). Os outros padrões são detectados pela estrutura do frame, não por
hashes. Num jogo novo, os nomes dos interruptores mudam com o perfil (`optxbox profile` imprime o
perfil do DBZ3 em JSON para copiar e adaptar).

---

## 8. Ordem de trabalho num port novo

1. Port base funcionando (ver o guia de port, fases 0 e 1), com a instrumentação da secção 3.
2. **Rodada 1:** cena pesada + menu, com captura, nos aparelhos de teste (no DBZ3: Retroid
   Pocket 5 / Snapdragon 865 e RG Cube / Mali-G57). `report --adb`, colar no chat.
3. Ligar de uma vez as correções dos padrões encontrados (são patches/cvars já escritos).
4. **Rodadas seguintes:** sem captura, só `report --adb` para confirmar; captura de novo quando
   aparecer algo que os padrões conhecidos não explicam.
5. O que for novo nesse jogo vira padrão novo na ferramenta (perfil + detecção + documento).

---

## 9. Limitações

- A captura não vê uploads, barreiras nem os passes extras que o runtime cria: os fins de passe
  reais vêm da linha `fins de passe` do log.
- Os ms estimados servem para ordenar o que tentar; os números reais costumam ser maiores
  (o padrão A estimava ~1 ms e deu ~8 ms no Mali).
- Vértices só são lidos em draws pequenos (≤ 6 vértices, float 32 bits). MRT não é considerado.
- Validado num só jogo até agora (DBZ3).
