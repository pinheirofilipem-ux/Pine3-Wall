# Plano: Pine3 Wall

## 1. Visão
- **Integração HA** (`pine3_wall`, backend): o modelo da casa, as pessoas, os layouts, os módulos, os tablets, e o **editor e painel web**.
- **App Android nativa**: o produto no tablet de parede. Desenha os layouts definidos na web e tem as funções de parede
  (motion, wake, ecrã, screensaver de vídeo, campainha).
- **Web (nível 3)**: editor de layouts **e** painel completo no browser ou no telemóvel, com as mesmas páginas e módulos da app.
- **Módulos**: `home` (casa), `ambient` (ecrãs compostos), `calendar` (calendário de família, com iCloud) e `chores`
  (tarefas por pessoa e por dia). A arquitetura está preparada para mais.
- **Repositório próprio**: o Pine3 View fica congelado, só com correções, e convive com o Pine3 Wall até ser substituído.

## 2. Arquitetura

```
Pine3-Wall/
├── custom_components/pine3_wall/
│   ├── core/            modelo da casa, pessoas, layouts, tablets, contrato
│   ├── modules/
│   │   ├── home/        cartões da casa (vindos do Pine3 View)
│   │   ├── ambient/     ecrãs compostos (relógio analógico, tempo…)
│   │   ├── calendar/    calendário de família (iCloud via CalDAV no HA)
│   │   └── chores/      tarefas por dia e por pessoa
│   └── frontend/
│       ├── renderer/    desenha layouts e módulos (preview → painel completo)
│       └── editor/      construtor de layouts, pessoas, módulos, tablets, planta 3D
│
├── android/
│   ├── core/            ligação HA, renderer de layouts, dock, temas, animações
│   ├── modules/         home/, ambient/, calendar/, chores/
│   └── wall/            motion, wake, ecrã, campainha, screensaver de vídeo (local)
│
└── schema/
    ├── layout.schema.json   estrutura de um layout (páginas, secções, grelha com spans)
    ├── catalog.json         cartões e páginas de cada módulo + plataformas suportadas
    ├── tokens.json          temas, cores, raios, espaçamentos (gera Kotlin e CSS)
    └── examples/            payloads usados nos testes dos dois lados
```

## 3. Quem decide o quê

| Definido na web (guardado no HA) | Só no tablet (local) | Só nativo (a app executa) |
|---|---|---|
| Páginas, dock, secções, cartões, tamanhos | Screensaver de vídeo: pastas, zip, lista de clipes, ajuste, qualidade, cor de fundo | Animações completas, Liquid Glass |
| Tema, cores, língua | Tempo de entrada e de bloqueio do ecrã, sensibilidade ao movimento | Deteção de movimento, wake, ecrã a apagar, kiosk |
| Pessoas (foto, cor, alcunha) | Modo do screensaver: clássico, vídeo ou ecrã composto X | Reprodução de vídeo (MediaCodec) |
| Configuração dos módulos | Ligação (endereço/token), escala da interface | A campainha a acordar o ecrã |
| Que layout cada tablet usa | | |

A web como painel (nível 3) tem as mesmas páginas e módulos, mas sem motion, wake, kiosk nem screensaver de vídeo. A campainha aparece
como pop-up, sem acordar nada. As animações e o vidro são versões simplificadas em CSS.

## 4. Sistema de módulos

| Lado | O que cada módulo traz |
|---|---|
| Backend | Dados (guardados no HA), regras, comandos `pine3_wall/<módulo>/…`, entidades e serviços HA |
| Contrato | Manifesto (`id`, versão), cartões e páginas, opções, `platforms` |
| Web | Ecrã de configuração e renderer dos cartões (preview e painel) |
| App | Uma classe `Module` registada no `ModuleRegistry` |

Regras:
- Um módulo novo obriga a atualizar a app: é nativo, não se carrega código pela rede. Um cartão desconhecido mostra "atualiza a app".
- Um módulo pode sair primeiro na app e só depois na web, nunca o contrário. O editor avisa quando um cartão só existe numa plataforma.
- Os dados dos módulos também são expostos como entidades HA (`sensor`, `calendar`), para automações e para a app do HA.
- Cartões genéricos (uma lista ligada a qualquer entidade `todo` ou `calendar`) cobrem muitos casos sem um módulo novo.

## 5. Módulos

### `home`: a casa
Os cartões atuais migrados tal como estão: divisões, luzes, clima, estores, aspirador, câmaras, fechaduras, TV, tempo com avisos IPMA,
cenas, resumos e Casa 3D.

### `ambient`: ecrãs compostos
- Faces: relógio analógico (vários estilos), relógio digital grande, tempo, próximo evento, agenda de hoje, tarefas de hoje, fotografia,
  um sensor em destaque.
- Composição de 1 a 3 faces lado a lado, com estilo e cores escolhidos na web.
- Usos: como página no dock, como modo de screensaver (escolhido localmente) e, opcionalmente, por horário (modo noturno).

### `calendar`: calendário de família
- **Um só calendário "Família"** no iCloud, ligado ao HA pela integração **CalDAV** (`https://caldav.icloud.com`, Apple ID e palavra-passe
  específica de app). O módulo trabalha sobre entidades `calendar.*`, por isso também aceita Google, Outlook ou o calendário local do HA.
- Backend: lê os eventos com `calendar.get_events`, normaliza (dia inteiro, vários dias), guarda em cache e envia por subscrição.
- **Atribuição a pessoas pelo título**: o nome ou a alcunha da pessoa no início ou entre parênteses retos ("[Pedro] Natação",
  "Pedro: Natação"); vários nomes = vários donos; sem nome = "Família". O editor mostra uma pré-visualização da atribuição.
- Web: que calendário(s) entram, cor, privacidade (só "Ocupado") e quantos dias mostrar.
- Vistas: cartão "Próximos", página de agenda com Hoje (colunas por pessoa), Semana e Mês, detalhe do evento.
- Mais tarde: criar eventos no tablet, se o CalDAV no HA o permitir (a confirmar no início da fase).

### `chores`: tarefas da casa
- **Só a lista do que está definido**: não há "feito / por fazer".
- Web: tarefas (nome, ícone, momento do dia) e uma grelha semanal tarefa × dia → pessoa ("Pôr a mesa", segunda, Pedro).
- Tablet: cartão "Hoje" com o resumo por pessoa, cartão por pessoa, página da semana; realce da tarefa do momento do dia.
- HA: um `sensor` por pessoa com as tarefas de hoje (no estado o número, nos atributos a lista), para automações e notificações.

### "O dia da família" (junta `calendar` e `chores`)
Uma página com uma coluna por pessoa, cada uma com os eventos e as tarefas de hoje ("Pedro: 17:30 Natação · Pôr a mesa"), e uma face
equivalente no `ambient`.

## 6. Fases

| # | Fase | Conteúdo | Resultado |
|---|---|---|---|
| 0 ✅ | Repositório | Repo a partir do Pine3 View com o histórico, domínio `pine3_wall` e `applicationId` `pt.pine3.wall`, keystore própria nos secrets, versão única (`VERSION`), release por tag com o APK, CI, sem valores pessoais fixos | O mesmo que existia, instalável ao lado do Pine3 View |
| 1 ✅ | Contrato | `schema/`, `api_version` e `min_app_version`, `tokens.json` a gerar os temas em Kotlin e CSS, testes pytest e Kotlin com os mesmos `examples/` | A web e a app não se afastam sem um teste falhar |
| 2 ✅ | Layouts, módulos e pessoas | Documento de layout (grelha com spans), `ModuleRegistry` no backend e na app, cartões atuais no módulo `home`, `Pages.kt` a interpretar layouts (templates → secções → cartões), layout automático por omissão, pessoas no núcleo | A app desenha o que o HA lhe diz, com o mesmo aspeto |
| 3 ✅ | Editor web e renderer (níveis 1 e 2) | Renderer web pensado para o nível 3, construtor, pré-visualização com estados reais e ao vivo no tablet, rascunho/publicação/histórico, editor de planta 3D, ecrã de pessoas | A interface da app é construída na web |
| 4 ✅ | Tablets no HA | `register_panel`, dispositivo HA por tablet (movimento, ecrã, bateria, versão), serviços `wake`, `show_page`, `notify`, layout por tablet. O screensaver continua local | Tablets geridos e automatizáveis a partir do HA |
| 5 | Módulo `ambient` | Faces, composição na web, página e modo de screensaver | Primeiro módulo novo |
| 6 | Módulo `calendar` | Ligar o iCloud via CalDAV e confirmar o que é possível; leitura, "Próximos", Hoje/Semana/Mês, atribuição pelo título, privacidade, faces | Calendário de família |
| 7 | Módulo `chores` | Tarefas, grelha semanal, cartões "Hoje" e por pessoa, sensor por pessoa | Tarefas da casa |
| 8 | O dia da família | Página e face que juntam eventos e tarefas por pessoa | A vista "família" |
| 9 | Painel web completo (nível 3) | Renderer interativo, sheets de detalhe, dock, ecrã inteiro e `?kiosk`, telemóvel, campainha como pop-up, paridade dos módulos | As mesmas vistas da app no browser |
| 10 | Instalação e substituição | Emparelhamento por QR, aviso de atualização da app, kiosk (Device Owner), importação do Pine3 View, guia de migração | O Pine3 Wall substitui o Pine3 View |

## 7. Princípios
1. Nada do Pine3 View é alterado: domínio, URL do painel, armazenamento e `applicationId` são outros; a importação só lê.
2. O layout é gerado automaticamente por omissão: sem mexer em nada, a app fica igual à de hoje.
3. Compatibilidade explícita: `schema_version`, `api_version`/`min_app_version` e cartões desconhecidos com aviso em vez de erro.
4. Uma só fonte para o contrato: catálogo, tokens e exemplos em `schema/`, usados pelos testes dos dois lados.
5. O que é local fica local: screensaver de vídeo e lógica de ecrã e movimento continuam no tablet.

## 8. Riscos

| Risco | Mitigação |
|---|---|
| A Fase 2 é a reescrita maior da app | Incremental, e a app antiga continua instalada ao lado |
| O editor e os módulos crescem sem fim | Catálogo fechado, grelha simples, cada módulo começa com 2 ou 3 cartões |
| A app e a web afastam-se (nível 3) | Testes com os mesmos exemplos, `platforms` no catálogo, avisos no editor |
| Limitações do iCloud/CalDAV (atraso, criar eventos) | Validar no início da Fase 6; começar só com leitura |
| Restrições do Android 14+ (boot, wake) ao subir o `targetSdk` | Manter o 34 até à Fase 10; Device Owner como solução de fundo |

## 9. Decisões tomadas
- Nome: **Pine3 Wall**, domínio `pine3_wall`, app `pt.pine3.wall`.
- Web: editor **e** painel completo (nível 3).
- Screensaver de vídeo: local no tablet, com a lógica atual.
- Tarefas: só a lista do que está definido (sem marcar como feito).
- Calendário: um só calendário "Família" partilhado no iCloud; pessoas atribuídas pelo título do evento.
