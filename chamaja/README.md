# Chama Já

Fila virtual: o cliente escaneia o QR code, pega a senha no celular e acompanha a vez.
**"Sua vez, no seu tempo."** Documentação completa na pasta acima: `Chama Já — Documentação Inicial.md`.

Java 21 · Spring Boot 3.4 · Thymeleaf · Spring Security · JPA · PostgreSQL · SSE · ZXing

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
(aberta, pausada, fechada), zerar a fila, login, CSRF, promoções e QR code.

## Rodar com PostgreSQL / publicar na Railway

| Variável | Para quê | Padrão |
| --- | --- | --- |
| `DB_URL` | URL JDBC, ex.: `jdbc:postgresql://HOST:5432/railway` | `jdbc:postgresql://localhost:5432/chamaja` |
| `DB_USER` / `DB_PASSWORD` | credenciais do banco | `postgres` / `postgres` |
| `SEED_SENHA` | senha do operador criado na primeira execução | vazio: gera uma aleatória e escreve no log |
| `SEED_USUARIO` / `SEED_NOME` | usuário do operador e nome do estabelecimento | `operador` / `Mercado Demo` |
| `APP_BASE_URL` | endereço público usado no QR code, ex.: `https://chamaja.up.railway.app` | endereço da requisição |
| `PORT` | porta HTTP (a Railway define sozinha) | `8080` |

```bash
mvn -DskipTests package
java -jar target/chamaja-0.1.0.jar
```

Tabelas são criadas e atualizadas pelo Hibernate (`ddl-auto=update`), o que basta para o MVP.
Para uso real, troque por migrações (Flyway).

## Estrutura

```
src/main/java/br/fatec/chamaja/
  model/       Estabelecimento, Operador, Senha, Promocao (+ enums de estado)
  repository/  acesso ao banco (Spring Data JPA)
  service/     FilaService (regras da fila e do aviso), PromocaoService, SseHub (tempo real), QrCodeService
  web/         ClienteController (/q, /senha), PainelController e PromocaoController (/painel)
  config/      SecurityConfig, RateLimitFilter, DataInitializer
src/main/resources/templates/   telas Thymeleaf (cliente/ e operador/)
src/main/resources/static/      css e js
```

## Decisões e limitações do MVP

- Uma fila por estabelecimento; um operador por estabelecimento.
- Os avisos (3 números e "é a sua vez") aparecem com a página aberta, por tela, vibração e som.
  Notificação com o aparelho bloqueado (push/WhatsApp) fica para uma versão futura.
- "Uma senha por aparelho" usa cookie: protege contra duplo clique, não contra quem apaga o cookie.
- O SSE e o limite de requisições funcionam em memória: servem para uma instância só.
- As senhas e os nomes dos clientes são apagados todo dia às 04:00 (`chamaja.reset-cron`).
