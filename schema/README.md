# schema/: o contrato entre a integração, a web e a app

Tudo o que a integração (`custom_components/pine3_wall`), o painel web e a app Android (`android/`) têm de entender da mesma maneira
vive aqui. Os testes dos dois lados leem estes ficheiros, por isso uma alteração que um dos lados não acompanhe parte o CI.

| Ficheiro | O que é | Quem o usa |
|---|---|---|
| `home.schema.json` | JSON Schema do modelo da casa (`pine3_wall/home` e `pine3_wall/subscribe`), incluindo as definições partilhadas | `tests/test_contract.py` valida o modelo real e o exemplo contra ele |
| `examples/home.json` | Um modelo real (da casa de teste) | pytest (valida) e `android/.../ContractTest.kt` (lê com `Home.parse`) |
| `tokens.json` | Temas (fundos), cores dos cartões e de destaque | `tools/gen_tokens.py` gera `frontend/tokens.js` e `ThemeTokens.kt` |

## Versões
- `VERSION` (raiz): versão da integração e da app, sempre iguais.
- `API_VERSION` (`const.py` e `Compat.kt`): versão do contrato. **Acrescentar** campos opcionais não a muda; **remover ou mudar o significado**
  de um campo obriga a subi-la (e a atualizar `Compat.API_VERSION` na app).
- `MIN_APP_VERSION` (`const.py`): a app mais antiga que entende o modelo atual. Se a app for mais antiga, ou o contrato mais novo do que
  ela, a app mostra o que atualizar em vez de desenhar uma casa errada. O teste de ligação também o indica.

## Alterar
- **Modelo da casa**: muda `discovery.py`/`websocket.py`, atualiza `home.schema.json` e regenera o exemplo:
  `PINE3_UPDATE_EXAMPLES=1 pytest tests/test_contract.py`.
- **Temas e cores**: edita `tokens.json` e corre `python3 schema/tools/gen_tokens.py` (os ficheiros gerados não se editam à mão).
