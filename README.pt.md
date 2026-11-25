<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/brand/pine3-wall-logo-dark@2x.png">
    <img src="docs/brand/pine3-wall-logo@2x.png" alt="Pine3 Wall" width="420">
  </picture>
</p>

<p align="center"><a href="README.md">English</a> · <b>Português</b></p>

O **Pine3 Wall** transforma um tablet na parede no painel de controlo da casa. Tem três partes que partilham o mesmo modelo da casa:

| Parte | O que faz |
|---|---|
| **Integração do Home Assistant** (`pine3_wall`) | Monta a casa a partir das tuas áreas, dispositivos e entidades (sem YAML), guarda as pessoas, os layouts, o calendário da família e as tarefas, e regista cada tablet de parede como um dispositivo do Home Assistant que podes usar em automações. |
| **Editor e painel web** (barra lateral do Home Assistant) | Desenha o que os tablets mostram (páginas, secções, cartões, dock, ecrãs ambiente) e é também um painel completo no browser ou no telemóvel, com as mesmas vistas dos tablets. |
| **App Android nativa** (`pt.pine3.wall`) | O painel no tablet, sem WebView: acorda com o movimento, desliga o ecrã, mostra o screensaver de vídeo, abre a campainha e desenha tudo de forma nativa e fluida. |

> **Testado num OPPO Pad SE (11", ColorOS) montado na parede.** Outros tablets Android devem funcionar, mas a gestão de energia muda de
> marca para marca (ver [Preparar o tablet](#preparar-o-tablet)).

O Pine3 Wall sucede ao [Pine3 View](https://github.com/pinheirofilipem-ux/pine3-view). Usa outro domínio, outro endereço do painel, outro
armazenamento e outra app, por isso os dois podem estar instalados lado a lado durante a mudança.

## Conteúdo
- [Imagens](#imagens)
- [Instalação](#instalação)
- [Preparar o tablet](#preparar-o-tablet)
- [A casa](#a-casa)
- [Editor de layout](#editor-de-layout)
- [Módulos: ecrãs ambiente, calendário da família, tarefas e o dia da família](#módulos)
- [O tablet de parede (app Android)](#o-tablet-de-parede-app-android)
- [Os tablets no Home Assistant](#os-tablets-no-home-assistant)
- [O painel web](#o-painel-web)
- [Definições](#definições)
- [Como funciona](#como-funciona)
- [Desenvolvimento](#desenvolvimento)

## Imagens
O painel web e a app têm o mesmo aspeto; estas imagens são do painel web, com uma casa de exemplo.

| Início (Duas colunas) | Início (Relógio grande, fundo claro) |
|---|---|
| ![Início em duas colunas: relógio, pessoas, o que está ligado e tempo à esquerda, cenas e cartões à direita](docs/screenshots/home.jpg) | ![Início com relógio grande no fundo claro Linho](docs/screenshots/home-light.jpg) |
| **Início (Barra lateral, fundo Âmbar)** | **Divisão** |
| ![Início com barra lateral no fundo Âmbar](docs/screenshots/home-vivid.jpg) | ![Uma divisão com luzes, clima, TV e estores](docs/screenshots/room.jpg) |
| **Agenda: dia por pessoa** | **Agenda: semana** |
| ![Agenda do dia com uma coluna por pessoa e uma da família](docs/screenshots/agenda-day.jpg) | ![Agenda da semana](docs/screenshots/agenda-week.jpg) |
| **Agenda: mês** | **O dia da família** |
| ![Agenda do mês](docs/screenshots/agenda-month.jpg) | ![Uma coluna por pessoa com os eventos e as tarefas de hoje](docs/screenshots/family.jpg) |
| **Tarefas** | **Ecrã ambiente** |
| ![Tarefas de hoje por pessoa e a grelha da semana](docs/screenshots/chores.jpg) | ![Ecrã ambiente com relógio analógico, tempo e próximo evento](docs/screenshots/ambient.jpg) |
| **Ecrã ambiente: o dia da família** | **Screensaver (web)** |
| ![Ecrã ambiente com relógio digital e o dia da família](docs/screenshots/ambient-family.jpg) | ![Relógio do screensaver na web](docs/screenshots/screensaver.jpg) |
| **Luz (folha compacta)** | **Aspirador** |
| ![Folha compacta da luz com luminosidade, cores e o botão de ligar](docs/screenshots/light.jpg) | ![Folha do aspirador](docs/screenshots/vacuum.jpg) |
| **Ecrã de clima** | **Ar condicionado** |
| ![Todos os ares condicionados e a temperatura de cada divisão](docs/screenshots/climate.jpg) | ![Folha do clima com o anel e os modos](docs/screenshots/climate-card.jpg) |
| **Tempo e avisos do IPMA** | **Casa 3D** |
| ![Previsão com as próximas horas e dias](docs/screenshots/forecast.jpg) | ![Casa 3D com dois pisos e luzes acesas](docs/screenshots/house-3d.jpg) |
| **Editor de planta** | **Modo de edição** |
| ![Editor de planta com as divisões na grelha](docs/screenshots/plan.jpg) | ![Modo de edição: esconder, tamanho duplo e ordenar](docs/screenshots/edit-mode.jpg) |
| **Editor de layout** | **Editor de layout: ecrã ambiente** |
| ![Editor de layout: páginas, secções, cartões e dock](docs/screenshots/editor.jpg) | ![A compor um ecrã ambiente no editor](docs/screenshots/editor-ambient.jpg) |
| **Definições: calendário** | **Definições: tarefas** |
| ![Definições do calendário](docs/screenshots/settings-calendar.jpg) | ![Definições das tarefas com a grelha semanal](docs/screenshots/settings-chores.jpg) |
| **Definições: tablets** | **Definições: aparência** |
| ![Cada tablet de parede com o estado, o layout e Acordar / Testar mensagem](docs/screenshots/settings-tablets.jpg) | ![Definições de aparência](docs/screenshots/settings-appearance.jpg) |

No telemóvel:

| Início | O dia da família | Agenda | Luz |
|---|---|---|---|
| ![Início no telemóvel](docs/screenshots/phone-home.jpg) | ![O dia da família no telemóvel](docs/screenshots/phone-family.jpg) | ![Agenda da semana no telemóvel](docs/screenshots/phone-agenda.jpg) | ![Folha da luz no telemóvel](docs/screenshots/phone-light.jpg) |

*O screensaver de vídeo, o acordar com o movimento e as outras funções de parede só existem na app Android.*

## Instalação

### 1. A integração (HACS)
1. No Home Assistant abre o **HACS** → **⋮** → **Repositórios personalizados**.
2. Repositório `https://github.com/pinheirofilipem-ux/pine3-wall`, tipo **Integração** → **Adicionar**.
3. Procura **Pine3 Wall**, **Transferir**, e reinicia o Home Assistant.
4. **Definições → Dispositivos e serviços → Adicionar integração → Pine3 Wall**.
5. O **Pine3 Wall** aparece na barra lateral.

Precisa do Home Assistant 2024.7 ou mais recente. Para atualizar, transfere a versão nova no HACS e reinicia; o browser apanha os ficheiros
novos sozinho. Instalação manual: copia `custom_components/pine3_wall` para `/config/custom_components/`, reinicia e adiciona a integração.

### 2. A app
1. Transfere o `Pine3Wall-X.Y.Z.apk` dos [Releases](https://github.com/pinheirofilipem-ux/pine3-wall/releases) (a mesma versão da
   integração) e instala-o no tablet.
2. No Home Assistant, cria um **token de acesso de longa duração** (o teu perfil → Segurança).
3. Abre a app: põe o endereço do Home Assistant (um IP como `http://192.168.1.10:8123` é mais seguro do que `.local` no Android) e o token.
   O **Teste de ligação** verifica o DNS, o websocket, o token e a integração passo a passo.
4. Nas definições da app → **Permissões**, dá tudo até ficar tudo a verde: câmara (movimento), administrador do dispositivo (desligar o
   ecrã por completo), sem otimização de bateria e aparecer por cima de outras apps (arranque automático).

A app e a integração têm o mesmo número de versão; a app avisa quando uma delas é antiga demais para a outra.

## Preparar o tablet
Cada marca de Android mata as apps em segundo plano à sua maneira. No **OPPO Pad SE (ColorOS)** testado:
- **Definições da app → Bateria**: *Permitir atividade em segundo plano* e *Permitir arranque automático* ligados; utilização da bateria
  **sem restrições**.
- **Definições da app → Permissões**: desliga *Remover permissões se a app não for usada*. Mantém as notificações da app ligadas (o serviço
  de movimento precisa da sua notificação para continuar vivo).
- **Bateria**: poupança de energia desligada; *Otimizar utilização da bateria* / *otimização em suspensão* desligadas.
- **Recentes**: bloqueia a app (cadeado) para não ser fechada; fixar o ecrã também funciona.
- **Bloqueio do ecrã**: *Nenhum* ou *Deslizar* é o mais fiável (a app abre por cima do bloqueio, mas com PIN algumas versões acordam no
  ecrã de bloqueio).
- **Ecrã → Desligar ecrã automaticamente**: o valor mais alto; é a app que desliga o ecrã.
- Se a app antiga do Pine3 View ainda estiver instalada, para-a: só uma app de cada vez pode usar a câmara frontal para o movimento.

Depois reinicia o tablet. A app tem de arrancar sozinha e a entidade *Ligado* do tablet no Home Assistant tem de ficar ligada a noite toda.

## A casa
Tudo é montado automaticamente a partir do Home Assistant e atualizado quando acrescentas ou mudas dispositivos.

- **Início** com quatro disposições: **Relógio grande**, **Duas colunas**, **Barra lateral** ou **Clássica**, com data, quem está em casa,
  estado da casa (luzes e o que mais está ligado), tempo, cenas e uma grelha de cartões. No tablet os cartões têm todos a mesma altura e
  chegam até à dock.
- **Divisões** a partir das tuas áreas (ordenadas por piso), com botão para as luzes, o que está ligado, temperatura e humidade; um
  **ecrã por divisão** com filtros (Luzes, Clima, Estores, Multimédia…) e uma grelha de cartões.
- **Dispositivos** como cartões guiados pela entidade principal; as outras entidades do dispositivo (potência, bateria, filtros…) vêm junto.
  - **Luzes, TVs, tomadas e interruptores** abrem uma **folha compacta**: luminosidade, cores (com seletor de cor livre), controlos
    multimédia, as leituras do dispositivo e um botão grande de ligar. O ⚙ abre o dispositivo todo (definições e diagnóstico).
  - **Clima** (Tado, HomeKit, ares condicionados…): anel da temperatura, intervalo calor/frio, modos, ventilação, oscilação e presets; um
    **ecrã de Clima** com todos.
  - **Aspirador** (Roborock): estado, bateria, **rotinas**, iniciar/pausa/base/localizar, potência, **limpar as divisões escolhidas**
    (`roborock.get_maps`), o mapa e os controlos do dia a dia; um cartão largo na sua divisão com "limpar esta divisão".
  - **Câmaras**: só a última imagem (nunca liga o vídeo sozinha); o vídeo ao vivo ao tocar, com as portas ao lado ("escolher portas").
  - **Fechaduras** precisam de dois toques para abrir. **Estores**, ventoinhas, purificadores, leitores, Apple TV e HomePod têm cartões próprios.
- **Cartões de resumo**: Clima, Segurança (portas, janelas, fechaduras), Aspirador e A tocar.
- **Campainha** (entidades `event` de classe doorbell, p. ex. Comelit Intercom): pop-up com a última imagem da câmara da porta, a foto da
  chamada assim que chega, **Abrir porta** (um toque) e **Ver câmara**; no tablet acorda o ecrã.
- **Tempo** de uma entidade do Home Assistant ou do [Open-Meteo](https://open-meteo.com): agora, as próximas horas (deslizam de lado),
  7 dias, UV, risco de incêndio e **avisos do IPMA**.
- **Casa 3D**: divisões empilhadas por piso, iluminadas com a cor das luzes; rodar, aproximar, tocar numa luz ou abrir uma divisão. Um
  **editor de planta** (web) desenha divisões de qualquer forma e coloca cada dispositivo.
- **Cenas** ficam realçadas enquanto os seus dispositivos estão ligados; tocar outra vez desliga-os.
- **Modo de edição** (✎ na web, ⋮ no tablet): arrastar para ordenar, esconder, tamanho duplo.

## Editor de layout
*Definições → Layout → Abrir o editor* no painel web. O que constróis aqui é o que os tablets desenham.

- **Páginas**: Início, Divisões, Casa 3D, Cenas, Clima, Segurança, Agenda, Tarefas, Família, cada divisão e os teus **ecrãs ambiente**.
- **Secções** (relógio, pessoas, estado, tempo, cenas, grelha…) e **cartões** (divisões, dispositivos, resumos, *Próximos*, *Tarefas de
  hoje*), arrastados para ordenar, tamanho duplo, acrescentados ou tirados; a disposição do início; a **dock** (que vistas e por que ordem);
  grelha ou lista, relógio de 24 horas e animações.
- **Rascunho, Publicar e Histórico**: trabalhas num rascunho, publicas quando estiver pronto, voltas a uma versão anterior ou ao layout automático.
- **Rascunho nos tablets**: mostra o rascunho ao vivo em todos os tablets enquanto editas, sem publicar.
- **Variantes**: além do layout principal podes criar outros (p. ex. "Quartos") e escolher, por tablet, qual usa.

Sem layout publicado, o layout é feito automaticamente a partir das definições, por isso nada muda até publicares um.

## Módulos

### Ecrãs ambiente
Ecrãs para ver de longe (como o StandBy): **1 a 3 faces lado a lado**: relógio analógico (clássico, minimalista, forte), relógio digital,
data, tempo, um sensor em letra grande, quem está em casa, **próximo evento**, **agenda de hoje**, **tarefas de hoje** e **o dia da
família**, com uma cor de destaque e um **modo noturno** vermelho escuro. Um ecrã ambiente pode ser uma página da dock (tablet e web) e o
**screensaver** do tablet (definições da app → Screensaver → Ecrã ambiente).

### Calendário da família
Um calendário partilhado pela família, por exemplo um calendário **"Família" no iCloud** ligado pela integração **CalDAV** do Home
Assistant (servidor `https://caldav.icloud.com`, o teu Apple ID e uma palavra-passe específica de app); Google, Outlook ou o calendário
local também servem.
- *Definições → Calendário*: que calendários ler, os que só aparecem como **Ocupado**, dias à frente e a cor da família.
- **De quem é cada evento** vem do título: `[Pedro] Natação`, `Pedro: Natação`, `Pedro e Ana: Jantar` ou `Natação [Pedro]` (nome ou nome
  curto de *Definições → Pessoas*); sem nome, é da família toda. As definições mostram de quem seria um título.
- Página **Agenda**: **Dia** com uma coluna por pessoa e uma da família, **Semana** e **Mês**; toca num evento para ver a hora, o sítio e
  as pessoas.
- Cartão *Próximos* (de todos ou de uma pessoa) e as faces *Próximo evento* / *Agenda de hoje*.

### Tarefas
Quem faz o quê em cada dia da semana: só a lista do que está combinado, não há "feito".
- *Definições → Tarefas*: as tarefas (nome, ícone, momento do dia) e uma grelha semanal onde tocas em quem faz cada uma em cada dia.
- Página **Tarefas** (hoje por pessoa e a semana inteira), cartão e face *Tarefas de hoje*; a tarefa do momento do dia aparece realçada.
- Um **sensor por pessoa** no Home Assistant (dispositivo *Tarefas da casa*) com o número de tarefas de hoje e a lista nos atributos, para
  automações e notificações.

### O dia da família
Uma página **Família** com uma coluna por pessoa com os eventos e as tarefas de hoje ou de amanhã por hora (a tarefa do momento realçada,
os eventos que já passaram esbatidos), mais os eventos de toda a família, e uma face ambiente com uma linha por pessoa.

### Pessoas
*Definições → Pessoas*: a família vem das `person.*` do Home Assistant; escolhes a cor de cada pessoa, o nome curto e se aparece. O
calendário, as tarefas, o início e as faces ambiente usam-nas.

## O tablet de parede (app Android)
- **O movimento acorda o ecrã** (câmara frontal, sensibilidade ajustável) e a app **desliga-o por completo** ao fim de algum tempo
  (administrador do dispositivo), com fade para preto e de volta.
- **Screensaver** antes de o ecrã desligar (tempos de entrada e de bloqueio em segundos):
  - **Clássico**: relógio escurecido com deriva lenta.
  - **Vídeo**: clipes em ecrã inteiro com a hora e a data, fade entre clipes, ajuste e qualidade; clipes de **uma ou várias pastas do
    tablet**, um pacote zip, uma lista de endereços ou os aéreos da Apple TV, com interruptor por clipe. O 4K HEVC que o leitor normal recusa
    passa pelo MediaCodec. Os vídeos ficam no tablet.
  - **Ecrã ambiente** do editor de layout.
- **A campainha acorda o ecrã** e abre o pop-up; os serviços abaixo também o acordam.
- **Deslizar para a esquerda/direita** passa para a vista seguinte/anterior da dock.
- Dock **Liquid Glass** (a lente desliza e estica como uma gota, e arrasta-se com o dedo), animações elásticas, 26 temas, três estilos de
  vidro, português e inglês, escala da interface.
- Todos os ícones do Material Design (os mesmos do Home Assistant) e definições no mesmo formato do painel web, mais uma secção **App**
  (ligação, tamanho da interface, teste da campainha, arranque automático, permissões).
- Arranca sozinha depois de reiniciar; religa sozinha e mantém-se ligada com o ecrã desligado.
- Atalho para as definições: dois dedos premidos durante 2 s, ou o menu ⋮.

Mais detalhe (compilar, assinatura, releases) em [android/README.md](android/README.md).

## Os tablets no Home Assistant
Cada tablet que se liga aparece como um **dispositivo "Pine3 Wall &lt;nome&gt;"** com: *Ligado*, *Movimento* (câmara frontal), *Ecrã*,
*Bateria*, *Brilho do ecrã*, *Página* e um botão **Acordar**. O nome, o layout (principal ou uma variante) e a remoção estão em
*Definições → Tablets* no painel web, que também tem os botões **Acordar** e **Testar mensagem**.

Serviços para automações (um ou mais tablets; sem alvo, todos):

| Serviço | O que faz |
|---|---|
| `pine3_wall.wake` | Liga o ecrã (e mantém-no ligado durante `seconds`). |
| `pine3_wall.show_page` | Abre uma página: `home`, `rooms`, `calendar`, `chores`, `family`, `room/<área>`, `ambient/<id>`… |
| `pine3_wall.show_camera` | Abre o vídeo ao vivo de uma câmara. |
| `pine3_wall.notify` | Mostra uma mensagem no ecrã ("O jantar está na mesa"), acordando-o. |
| `pine3_wall.screensaver` | Liga ou desliga o screensaver. |

## O painel web
O mesmo painel dos tablets, em qualquer browser ou no telemóvel:
- Com um **layout publicado** mostra exatamente o que os tablets mostram: a disposição e as secções do início, a ordem, o tamanho e o tipo
  de cada cartão, os dispositivos de cada divisão e a dock com os ecrãs ambiente.
- **Telemóvel**: uma coluna, a agenda e o dia da família em lista, deslizar entre vistas.
- **Ecrã inteiro** (*Definições → Início → Este dispositivo*) e **modo quiosque**: acrescenta `?kiosk` ao endereço
  (`http://homeassistant.local:8123/pine3-wall?kiosk`) para esconder a barra lateral do Home Assistant.
- Opções por dispositivo (ficam nesse browser): barra lateral, manter o ecrã ligado, qualidade da Casa 3D (completa, leve, imagem ou planta
  2D), efeitos leves, animações e pop-up da campainha.

## Definições
No painel web (⋮ → Definições) ou na app. Ficam guardadas no Home Assistant e valem para todos os tablets e browsers:
**Aparência** (língua, grelha ou lista, cor dos cartões e de destaque, 26 fundos, automático de dia e de noite), **Início** (disposição,
secções, fonte do tempo, screensaver), **Pessoas**, **Calendário**, **Tarefas**, **Divisões** (ordem, escondidas, tamanho na Casa 3D),
**Entidades** (exatamente o que aparece), **Planta 3D**, **Navegação** (dock), **Layout** (o editor), **Tablets** e **Sistema**.

## Como funciona
- **A casa** (`discovery.py`) é montada a partir dos registos do Home Assistant: áreas → divisões (ordenadas por piso), dispositivos →
  cartões guiados pela entidade principal, relés de vários canais separados, nome da divisão tirado do nome dos dispositivos, entidades
  escondidas/de diagnóstico/de configuração deixadas de fora.
- **O contrato** entre a integração e a app está em [`schema/`](schema/): o modelo da casa, o documento de layout, o catálogo de módulos,
  cartões e faces, os tokens de design (temas e cores, gerados para Kotlin e CSS) e exemplos testados dos dois lados. Um tipo de cartão que
  o tablet não conhece mostra "Atualiza a app" em vez de falhar.
- **Uma só versão** (`VERSION`) para a integração e para a app; os releases têm a tag `vX.Y.Z` e trazem o APK.

## Desenvolvimento
```bash
pip install -r requirements_test.txt
pytest
```
```bash
cd android
./gradlew :app:testReleaseUnitTest :app:assembleRelease
```
O CI corre o hassfest, a validação do HACS, o pytest, os testes Kotlin e compila o APK assinado. O plano e as fases estão em
[PLANO.md](PLANO.md). A seguir: emparelhamento por QR code, aviso de atualização na app, quiosque como device owner e importação das
definições do Pine3 View.

Tipos de letra: [Outfit](https://github.com/Outfitio/Outfit-Fonts) e [Figtree](https://github.com/erikdkennedy/figtree) (SIL OFL 1.1).
Ícones: [Material Design Icons](https://pictogrammers.com/library/mdi/) (Apache 2.0).

## Licença
MIT (ver `LICENSE`). Os tipos de letra estão sob a SIL Open Font License 1.1 (ver `NOTICE`).
