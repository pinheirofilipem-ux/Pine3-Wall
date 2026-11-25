# Pine3 Wall: app Android

A app Android **nativa** (sem WebView) do Pine3 Wall: mostra a casa que a integração `pine3_wall` monta no Home Assistant e
junta, num tablet de parede, acordar com o movimento, desligar o ecrã sem luz, o screensaver e a campainha.

A app e a integração têm **a mesma versão** (ficheiro `VERSION` na raiz). Pode estar instalada ao lado da app antiga do Pine3 View
(`applicationId` `pt.pine3.wall`), mas precisa da integração **Pine3 Wall**, não da Pine3 View.

> Testada num OPPO Pad SE de 11" (ColorOS, 90 Hz) montado na parede; outros tablets podem comportar-se de forma diferente
> (ver "Num tablet ColorOS" e o passo a passo em [Preparar o tablet](../README.pt.md#preparar-o-tablet)).

## Ligação ao Home Assistant
- WebSocket com token de acesso de longa duração. Lê o **mesmo modelo da casa** do painel web (`pine3_wall/home`, `pine3_wall/subscribe`)
  e grava as definições de volta (`pine3_wall/settings`), por isso o que mudas aqui vale também no painel web.
- Endereço por omissão: `http://homeassistant.local:8123/pine3-wall` (só a origem conta para a ligação). O Android muitas vezes não
  resolve nomes `.local`: se falhar, usa o IP do HA.
- Religa sozinha, mantém-se viva com o ecrã desligado e tem um **teste de ligação** passo a passo (DNS, WebSocket, token, Pine3 Wall).

## Ecrãs
- **Início** com as 4 disposições do painel web (clássica, topo, colunas, barra lateral), com as medidas do CSS (px = dp, `vw`, `clamp`).
- **Divisões**, **uma divisão** (filtros por tipo, cenas e dispositivos), **Cenas**, **Clima**, **Segurança** e **Casa 3D**.
- **Casa 3D** desenhada num canvas com a planta que desenhaste no painel web (posições, formas livres, pisos, luzes e equipamentos):
  as divisões brilham com a cor das luzes acesas, arrastas para rodar, pinça para aproximar, tocas numa luz para a ligar e numa
  divisão para a abrir. O **editor de planta** continua só no painel web.
- Grelha ou **lista**, divisões e dispositivos de **tamanho duplo**.
- **Modo de edição** (menu ⋮): arrastar para ordenar, esconder, tamanho duplo e repor o que foi escondido. Grava as mesmas definições
  do painel web.
- Todos os ícones do Material Design Icons (7447, os mesmos do HA), 26 temas, português e inglês, escala da interface.

## Cartões e janelas
- **Luzes** (luminosidade e cor, como o cartão do painel web), estores, **clima** (anel e modos), multimédia, ventoinha, fechadura e o
  resto das entidades de cada dispositivo. Os estados dos botões (cartões e Casa 3D) refletem sempre o estado real.
- **Aspirador**: cartão largo com comandos rápidos e janela com rotinas, potência, escolha das divisões a limpar e o mapa (Roborock).
- **Câmara**: o cartão mostra só a última imagem guardada (nunca liga a câmara); o vídeo corre dentro da janela, com abrir a porta
  (um toque) e +2 minutos. Há um marcador de carregamento enquanto a imagem não chega.
- **Tempo** (entidade do HA ou Open-Meteo para a localização da casa): cartão e janela de previsão, com as horas a deslizar para a
  esquerda como no iPhone, 7 dias com barra de temperaturas, nascer e pôr do sol, humidade, vento, UV, risco de incêndio e **avisos do IPMA**.
- **Campainha**: pop-up com a última imagem da câmara da porta; a foto do toque substitui-a assim que chega. Tem "Ver câmara" e abrir a
  porta. A porta segue as regras do painel web (as escolhidas para a câmara, `release_entity` da campainha, fechaduras da divisão) e
  pode ser escolhida em "Escolher portas". Há interruptor do pop-up e botão **Testar a campainha** nas definições da App.
  Se o ecrã estiver em screensaver ou apagado, a campainha acorda-o e abre o pop-up.

## Ecrã de parede
- A câmara frontal deteta movimento e **acorda o ecrã**, que **desliga sem luz** (administrador de dispositivo) ao fim de X segundos, com
  fade para preto e de volta, à mesma velocidade. Toques em qualquer janela (incluindo as definições) contam como atividade.
  Arranque automático depois de reiniciar.
- **Campainha que acorda o ecrã**: por omissão **qualquer campainha** do HA (`event.*` de classe doorbell), só em `ring`, durante 120 s;
  pode-se escolher uma entidade concreta.
- **Screensaver** (secção própria nas definições, com a entrada e o bloqueio juntos):
  - *Entrada* em segundos (barra até 120 s) e *bloqueio do ecrã* (3 a 180 s). O screensaver entra sempre **antes** do bloqueio e fica
    **por cima de tudo**; ao passar para o bloqueio o vídeo mantém-se até o ecrã ficar negro.
  - *Clássico*: relógio escurecido com deriva lenta.
  - *Vídeo*: clipes em ecrã inteiro com hora e data desenhadas à mão (sem cortes), que trocam de lado ao acaso a cada clipe e a cada
    90 s, e fade entre clipes. Ajuste do vídeo (pela altura sem esticar, preencher ou inteiro) e qualidade.
  - *Fontes*: **uma ou várias pastas do tablet** (escolhidas no seletor de pastas do Android, ficheiros `.mov`/`.mp4`, com as subpastas), um **pacote zip** descarregado
    uma vez para o tablet, uma lista `.json`/`.txt` de endereços, endereços extra e, opcionalmente, o ficheiro de recursos da Apple TV.
  - *Lista de clipes* com o tamanho de cada um e interruptor por clipe. Por omissão ignora intro, loop, outro e ficheiros pequenos.
  - Se o leitor normal do Android recusar um clipe (HEVC 4K), usa um descodificador alternativo (MediaCodec).
  - *Fechar janelas após* (0 a 120 s) e *sensibilidade ao movimento* da câmara frontal.
- As janelas normais (detalhe de dispositivo, previsão, escolha de portas) fecham sozinhas ao fim do tempo definido em Screensaver
  (por omissão 10 s, 0 = nunca); a câmara e a campainha ficam abertas.
- Atalho para as definições: dois dedos premidos durante 2 s, ou o menu ⋮.
- **Deslizar entre vistas**: para a esquerda ou para a direita passa para a vista seguinte ou anterior da dock (dá a volta no fim). Não
  funciona dentro de uma divisão, no modo de edição, por cima da Casa 3D nem a partir de uma fila que desliza de lado (cenas, horas do
  tempo, entradas).

## Ecrãs ambiente
- Ecrãs compostos (módulo `ambient`), criados no **Editor de layout** do painel web (*Novo ecrã ambiente*): 1 a 3 faces lado a lado —
  relógio analógico (clássico, minimalista ou forte), relógio digital, data, tempo, um sensor em destaque e quem está em casa —, cor de
  destaque e **modo noturno** (vermelho escuro, pouco brilho).
- Cada ecrã pode ter um botão na **dock** e pode ser o **screensaver** do tablet (definições da app → Screensaver → Ecrã ambiente).

## O tablet no Home Assistant
- Ao ligar-se, a app regista o tablet no HA como um **dispositivo** "Pine3 Wall <nome>" com: *Ligado*, *Movimento* (câmara frontal),
  *Ecrã*, *Bateria*, *Brilho do ecrã*, *Página* e o botão *Acordar*. O nome muda-se no painel web (Definições → Tablets).
- Serviços para automações (alvo: um ou mais tablets; sem alvo, todos): `pine3_wall.wake`, `pine3_wall.show_page` (ex. `room/sala`),
  `pine3_wall.show_camera`, `pine3_wall.notify` (mensagem no ecrã) e `pine3_wall.screensaver`.
- Cada tablet pode usar o layout principal ou uma **variante** criada no Editor de layout (Definições → Tablets → Layout).

## Definições (mesmo formato do painel web)
Aparência, Início, Divisões, Entidades, **Screensaver**, Planta 3D, Navegação, Sistema, mais uma secção inicial **App** com o que é só do
dispositivo (ligação, tamanho da interface, campainha e teste, arranque, permissões). As alterações de aspeto aplicam-se na hora, sem
voltar ao topo, e o botão Guardar fica ativo a qualquer alteração (incluindo a língua e o endereço/token).
- **Estilo do vidro** (Aparência): *Atual*, *Suave* ou *Liquid Glass* (aro de luz, brilho, lente com relevo e, nas janelas, desfoque real
  em Android 12 ou mais recente).
- Cor de fundo e paletas (incluindo pastel), temas, animações, língua (PT/EN), disposição (grelha/lista).

## Dock "Liquid Glass"
A lente de vidro desliza com mola por baixo do separador ativo e estica como uma gota. Também se **agarra e arrasta** com o dedo: cresce,
segue o dedo e assenta no separador mais próximo. Animações elásticas por toda a app (desligam-se com "Animações").

## Fora do âmbito
Editor de planta e editor de layout (no painel web), formato de telemóvel (o painel web serve para isso), vídeo MJPEG, modos 3D leve e planta 2D, e leitor de música
(Apple Music/AirPlay não está implementado).

## Num tablet ColorOS (OPPO)
O ColorOS mata apps em segundo plano com mais agressividade. Convém: bateria da app sem restrições, arranque automático ligado, app
bloqueada nos recentes, poupança de energia inteligente desligada e, nas permissões, desligar "remover permissões se a app não for usada".

## Compilar
```bash
cd android
./gradlew :app:assembleRelease
```
O APK fica em `app/build/outputs/apk/release/`. A versão (`versionName` e `versionCode`) vem do ficheiro `VERSION` na raiz.

## Releases e assinatura
- Cada push compila o APK (artefacto do workflow **Android**). No `main`, atualiza também a pre-release `android-latest`.
- Uma tag `vX.Y.Z` (igual ao `VERSION`) cria o release com o APK `Pine3Wall-X.Y.Z.apk`.
- Para que cada APK instale **por cima** do anterior (sem perder o token e as definições), todos têm de ser assinados com a mesma chave.
  Cria-a uma vez e guarda-a nos secrets do repositório (*Settings → Secrets and variables → Actions*):
  ```bash
  keytool -genkeypair -v -keystore pine3.jks -alias pine3 -keyalg RSA -keysize 4096 -validity 36500
  base64 -w0 pine3.jks
  ```
  | Secret | Valor |
  |---|---|
  | `PINE3_KEYSTORE_BASE64` | o resultado do `base64` |
  | `PINE3_KEYSTORE_PASSWORD` | a palavra-passe da keystore |
  | `PINE3_KEY_ALIAS` | `pine3` |
  | `PINE3_KEY_PASSWORD` | a palavra-passe da chave |

  Guarda o `pine3.jks` e as palavras-passe num sítio seguro: sem eles, uma app nova não instala por cima da antiga.
  Sem estes secrets o CI assina com uma chave debug descartável (avisa no log).

## Créditos
Ícones: [Material Design Icons](https://pictogrammers.com/library/mdi/) (Apache 2.0). Tipos de letra Outfit e Figtree (SIL OFL 1.1).

