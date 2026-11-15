# Chama Já

Fila virtual: o cliente escaneia o QR code, pega a senha no celular e acompanha a vez.
**"Sua vez, no seu tempo."** Documentação completa na pasta acima: `Chama Já — Documentação Inicial.md`.

Java 21 · Spring Boot 3.4 · Thymeleaf · Spring Security · JPA · Flyway · PostgreSQL (Supabase) · SSE · ZXing

## Rodar localmente (sem PostgreSQL)

```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

O perfil `dev` usa um banco H2 em memória (some ao parar o app) e cria os dados iniciais:

- Operador: `operador` / `operador123` → <http://localhost:8080/login>
- A página do cliente aparece no log: `Página do cliente: /q/<codigo>` → <http://localhost:8080/q/CODIGO>
- No painel, **QR code** gera a página para imprimir.

## Testes

```bash
mvn test
```

Cobrem a regra do aviso com até 3 números de diferença, posição na fila, estados da fila
(aberta, pausada, fechada), zerar a fila, login, CSRF, promoções e QR code. O
`MigracaoPostgresTest` sobe um PostgreSQL de verdade (embutido, sem Docker), aplica as migrações
e confere se o schema bate com as entidades.

---

## Publicar: Supabase (banco) + Railway (aplicação)

### 1. Supabase: criar o banco

1. Em <https://supabase.com>, **New project**. Anote a **senha do banco** (ela só aparece agora).
   Escolha uma região próxima da Railway (ex.: *East US*) para as consultas serem rápidas.
2. Clique em **Connect** (no topo do projeto) e abra a aba **Session pooler**. Anote:
   - **Host**, parecido com `aws-0-us-east-1.pooler.supabase.com`
   - **Porta** `5432`
   - **Usuário**, no formato `postgres.<ref-do-projeto>` (o `.<ref>` faz parte do usuário)

> **Use o Session pooler, não a conexão direta.** A conexão direta do Supabase (`db.<ref>.supabase.co`)
> só tem IPv6 e a Railway não sai por IPv6. O pooler em modo *session* (porta 5432) funciona
> por IPv4 e aceita o que a aplicação usa (travas de linha e transações).

Não é preciso criar tabelas: o Flyway cria tudo no primeiro start, num schema próprio chamado
`chamaja`. Isso mantém os nomes dos clientes fora do schema `public`, que o Supabase expõe numa API
REST aberta; as tabelas também nascem com RLS ligado e sem políticas.

### 2. Railway: criar o serviço

1. <https://railway.com> → **New Project** → **Deploy from GitHub repo** → `LuanV1udes/Chama-Ja`.
2. No serviço, **Settings → Root Directory** = `/chamaja` (o repositório tem a documentação na raiz
   e o código nesta pasta). A Railway usa o `Dockerfile` e o `railway.toml` daqui.
3. **Variables**: crie as variáveis da tabela abaixo.
4. **Settings → Networking → Generate Domain**. Copie o endereço, volte em **Variables** e
   preencha `APP_BASE_URL` com ele (sem barra no fim). A Railway reinicia sozinha.

| Variável | Valor | Obrigatória |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://HOST_DO_POOLER:5432/postgres?sslmode=require` | sim |
| `DB_USER` | `postgres.<ref-do-projeto>` | sim |
| `DB_PASSWORD` | a senha do banco | sim |
| `SEED_SENHA` | senha do primeiro operador (use uma forte) | recomendada |
| `SEED_USUARIO` | usuário do operador (padrão `operador`) | não |
| `SEED_NOME` | nome do estabelecimento (padrão `Mercado Demo`) | não |
| `APP_BASE_URL` | `https://seu-app.up.railway.app` (endereço do QR code) | sim, depois de gerar o domínio |
| `DB_POOL_SIZE` | conexões do pool (padrão `5`) | não |

`SPRING_PROFILES_ACTIVE=prod`, o horário de Brasília e a porta já vêm no `Dockerfile` e na Railway.

Se `SEED_SENHA` ficar vazia, uma senha aleatória é criada e escrita **uma vez** no log do primeiro
start (aba **Deployments → View logs**, procure `Operador 'operador' criado com a senha gerada`).

### 3. Conferir

- O log deve terminar com `Página do cliente: /q/<codigo>`. Esse é o endereço que o QR code abre.
- Entre em `https://seu-app.up.railway.app/login` e faça login com o operador.
- No Supabase, **Table Editor → schema `chamaja`** mostra as tabelas, e o schema `public` fica vazio.

### Problemas comuns

| Sintoma no log | Causa e solução |
| --- | --- |
| `Network is unreachable` / `UnknownHost` | Está usando a conexão direta (IPv6). Troque pelo host do **Session pooler**. |
| `password authentication failed` / `Tenant or user not found` | O usuário precisa ser `postgres.<ref>`, não só `postgres`. Confira também a senha. |
| `prepared statement "S_1" already exists` | Está na porta 6543 (modo *transaction*). Use a **5432** (modo *session*). |
| `Schema-validation: missing table` | A migração não rodou: confira `SPRING_PROFILES_ACTIVE=prod` e se `DB_URL` aponta para o banco certo. |
| Healthcheck falha no deploy | Veja o log: quase sempre é erro de conexão com o banco (variáveis `DB_*`). |

### Mudar o banco depois

Nunca edite `V1__criar_tabelas.sql` depois que ele foi aplicado em produção. Para mudar o schema,
crie `V2__descricao.sql` na mesma pasta (`src/main/resources/db/migration`).

---

## Estrutura

```
src/main/java/br/fatec/chamaja/
  model/       Estabelecimento, Operador, Senha, Promocao (+ enums de estado)
  repository/  acesso ao banco (Spring Data JPA)
  service/     FilaService (regras da fila e do aviso), PromocaoService, SseHub (tempo real), QrCodeService
  web/         ClienteController (/q, /senha), PainelController e PromocaoController (/painel)
  config/      SecurityConfig, RateLimitFilter, DataInitializer
src/main/resources/
  templates/   telas Thymeleaf (cliente/ e operador/)
  static/      css e js
  db/migration migrações do banco (Flyway, só no perfil prod)
Dockerfile, railway.toml   build e deploy na Railway
```

## Decisões e limitações do MVP

- Uma fila por estabelecimento; um operador por estabelecimento.
- Os avisos (3 números e "é a sua vez") aparecem com a página aberta, por tela, vibração e som.
  Notificação com o aparelho bloqueado (push/WhatsApp) fica para uma versão futura.
- "Uma senha por aparelho" usa cookie: protege contra duplo clique, não contra quem apaga o cookie.
- O SSE e o limite de requisições funcionam em memória: servem para uma instância só. Não aumente
  o número de réplicas na Railway.
- As senhas e os nomes dos clientes são apagados todo dia às 04:00, horário de Brasília
  (`chamaja.reset-cron`).
- A fonte (Outfit) vem do Google Fonts; sem internet o sistema usa uma fonte padrão.
