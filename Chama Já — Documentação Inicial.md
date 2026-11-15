# Chama Já — Documentação Inicial

Nov 15, 2026 · @Luan

## 1. Visão geral

O Chama Já é um aplicativo web de fila virtual: o cliente escaneia um QR code na entrada do estabelecimento, recebe uma senha no celular e acompanha a vez sem ficar parado na fila. Slogan: **"Sua vez, no seu tempo."**

**Problema.** Filas físicas geram aglomeração, perda de tempo e uma experiência ruim para quem espera. Enquanto esperam, os clientes também não veem as ofertas do local.

**Objetivo.** Eliminar a aglomeração nas filas e usar o tempo de espera para divulgar promoções do estabelecimento.

**Caso de uso principal.** Supermercados e lojas com atendimento por senha (açougue, padaria, atendimento ao cliente).

**Natureza do projeto.** Trabalho acadêmico (Fatec Ourinhos). O escopo é enxuto: um MVP com duas interfaces, uma para o cliente e uma para o operador.

## 2. Perfis de usuário

O sistema tem dois perfis, cada um com sua interface.

| Perfil | Quem é | O que precisa fazer | Acesso |
| --- | --- | --- | --- |
| Cliente | Pessoa que vai ao estabelecimento | Pegar senha, acompanhar a vez, ser avisado quando estiver perto, ver promoções | Sem cadastro, pelo QR code |
| Operador | Funcionário ou gerente do estabelecimento | Chamar senhas, gerenciar a fila, cadastrar promoções | Login com usuário e senha |

## 3. Escopo do MVP

O MVP cobre o ciclo completo de uma fila: entrar, esperar, ser avisado, ser chamado.

**Dentro do escopo**

- QR code rotativo exibido numa tela na entrada do estabelecimento (muda a cada minuto)
- Retirada de senha pelo celular, sem instalar app
- Acompanhamento da posição na fila em tempo real
- Aviso na tela do cliente quando faltarem até 3 números para a vez dele
- Aviso na tela do cliente quando a senha for chamada
- Avisos no celular por Web Push (notificação com a tela bloqueada), que o cliente ativa com um toque depois de pegar a senha
- Painel do operador para chamar, repetir, pular e finalizar senhas
- Cadastro, edição e remoção de promoções
- Exibição das promoções ativas para o cliente

**Fora do escopo (versões futuras)**

- Notificação por WhatsApp ou SMS (o aviso com a tela bloqueada é feito por Web Push, que é grátis e não exige o telefone do cliente)
- Mais de um estabelecimento por conta (multiloja)
- Mais de uma fila por estabelecimento (setores com filas separadas)
- Relatórios e métricas de tempo de espera
- Painel de TV com senhas chamadas
- Pagamento ou integração com sistemas de PDV

## 4. Requisitos funcionais

Cada requisito tem um código para facilitar a referência nos testes e na apresentação.

| Código | Perfil | Requisito |
| --- | --- | --- |
| RF01 | Cliente | Acessar o sistema ao escanear o QR code rotativo exibido na entrada do estabelecimento |
| RF02 | Cliente | Informar o nome e retirar uma senha |
| RF03 | Cliente | Ver a senha, a posição na fila e quantas pessoas estão à frente |
| RF04 | Cliente | Ver a senha atual sendo chamada |
| RF05 | Cliente | Ser avisado quando faltarem até 3 números para a sua senha: faixa de aviso na tela (com vibração e som uma única vez) e, se ele ativou os avisos no celular, também por notificação, mesmo com a tela bloqueada |
| RF06 | Cliente | Ser avisado quando for chamado (ou quando a chamada for repetida), na tela e, se ativou os avisos no celular, por notificação |
| RF07 | Cliente | Sair da fila antes de ser chamado |
| RF08 | Cliente | Ver a lista de promoções ativas enquanto espera |
| RF09 | Operador | Fazer login para acessar o painel |
| RF10 | Operador | Chamar a próxima senha |
| RF11 | Operador | Repetir a chamada, pular ou finalizar uma senha |
| RF12 | Operador | Ver a fila completa com status de cada senha |
| RF13 | Operador | Abrir, pausar, fechar e zerar a fila do dia |
| RF14 | Operador | Cadastrar promoção com título, descrição, preço e validade |
| RF15 | Operador | Editar, ativar, desativar e excluir promoções |
| RF16 | Operador | Obter o link da tela de entrada (tablet da porta, que exibe o QR code rotativo) e gerar um novo link quando necessário |

**Regras de negócio**

- **Aviso de proximidade (RF05).** O aviso dispara quando a diferença entre o número da senha do cliente e o número da senha atualmente chamada for **menor ou igual a 3**. Exemplo: se a senha 014 está sendo chamada, o cliente com a senha 017 é avisado. A faixa de aviso permanece na tela enquanto a condição valer; a vibração e o som do aparelho acontecem uma única vez por senha. Se o cliente entrar na fila já dentro dessa distância, ele é avisado ao retirar a senha.
- **Chamada (RF06).** Quando a senha do cliente é chamada, a tela troca a posição por um aviso destacado de que é a vez dele.
- **Repetir chamada (RF11).** Reenvia o aviso de chamada ao cliente; o status da senha continua "chamada".
- **Pular (RF11).** A senha pulada não volta sozinha para a fila. Se o cliente aparecer depois, precisa retirar uma nova senha.
- **Estados da fila (RF13).** *Aberta*: aceita novas senhas e chama normalmente. *Pausada*: não aceita novas senhas, mas o operador continua chamando as já existentes. *Fechada*: não aceita novas senhas e não chama.
- **Numeração.** As senhas são sequenciais, com 3 dígitos (ex.: 014), e a contagem reinicia quando a fila do dia é zerada.
- **QR code rotativo (RF01 e RF16).** O QR exibido na tela de entrada carrega um código assinado pelo servidor que muda a cada minuto e vale só para o minuto atual e o anterior. Uma foto do QR deixa de funcionar em poucos minutos, então não dá para pegar senha de casa com ela. Ao escanear um código válido, o aparelho recebe uma permissão de 10 minutos e de **uso único**: dá tempo de digitar o nome, mas não de pegar várias senhas em sequência. Sem escanear, a página só pede para escanear o QR na entrada.
- **Avisos no celular (Web Push).** Assim que o cliente pega a senha, aparece uma mensagem animada: "Quer ser avisado?" com **Permitir notificações** e **Agora não**. Se ele permite, o navegador se inscreve no serviço de push e a inscrição é ligada à senha dele. O servidor envia uma notificação quando faltam até 3 números (uma vez só), quando a senha é chamada e quando o operador repete a chamada. Se a página do cliente está aberta e visível, ela mesma avisa e o push não duplica. Quem já permitiu antes não é perguntado de novo. A inscrição some quando a senha termina (finalizada, pulada, cancelada), ao zerar a fila e no reset diário. Por segurança, o servidor só envia para endereços HTTPS dos serviços de push dos navegadores (Google, Mozilla, Apple e Microsoft), para que ninguém use o envio contra a rede interna.
- **Equipamentos sugeridos.** *Tablet externo (porta):* abre o link secreto da tela de entrada e só mostra o QR (sem login, sem acesso ao painel). *Tablet interno (atendimento):* painel do operador. *Reserva (celular ou computador):* também abre o painel, ao mesmo tempo e com o mesmo login, e pode abrir o link da tela no lugar do tablet externo. Se o link da tela vazar, o operador gera outro no painel e o antigo deixa de funcionar.

## 5. Requisitos não funcionais

| Código | Categoria | Requisito |
| --- | --- | --- |
| RNF01 | Usabilidade | O cliente deve pegar a senha em até 3 toques, sem instalar nada |
| RNF02 | Compatibilidade | Funcionar nos navegadores móveis atuais (Chrome e Safari) |
| RNF03 | Desempenho | A posição na fila e os avisos atualizam em até 5 segundos |
| RNF04 | Responsividade | Tela do cliente pensada primeiro para celular; painel do operador para tablet ou computador |
| RNF05 | Segurança | Painel do operador protegido por login; senhas armazenadas com hash (BCrypt); endpoints públicos do cliente com limite de requisições |
| RNF06 | Privacidade | Coletar o mínimo de dados do cliente (apenas nome) e apagá-los ao fim do dia, por rotina agendada ou ao zerar a fila. A inscrição de notificações do aparelho é apagada junto com a senha, assim que ela termina |
| RNF07 | Disponibilidade | Sistema deve manter a fila ao recarregar a página: a senha fica salva no aparelho por meio de um identificador único e não adivinhável (UUID) |

## 6. Fluxo principal

O cliente só precisa escanear, informar o nome e esperar; o operador abre a fila, cadastra promoções e chama as senhas, e tudo aparece na tela do cliente.

&#91;embedded content: fluxo principal · 2 perfis, 4 etapas do cliente\]

A etapa em destaque é a que entrega o valor do produto: enquanto espera, o cliente acompanha a posição, é avisado quando a vez se aproxima (até 3 números de diferença) e vê as promoções ativas.

## 7. Telas iniciais

O MVP tem seis telas: três para o cliente, no celular, e três para o operador, no painel. Os textos em cinza são conteúdo de exemplo.

### Cliente

&#91;embedded content: telas do cliente · 3 telas de celular\]

Quando a senha está a até 3 números da atual, a tela Minha senha exibe um aviso de que a vez está chegando. Quando a senha é chamada, a tela troca a posição por um aviso destacado de que é a vez do cliente.

### Operador

&#91;embedded content: telas do operador · 3 telas de painel\]

Chamar a próxima senha atualiza a tela de todos os clientes. Promoções ativas aparecem para o cliente; as inativas ficam guardadas para reutilizar.

## 8. Modelo de dados

Quatro tabelas bastam para o MVP.

| Tabela | Campos principais | Observação |
| --- | --- | --- |
| Estabelecimento | id, nome, código do QR code, estado da fila (aberta, pausada, fechada), senha atual | Um por conta e uma fila por estabelecimento no MVP |
| Operador | id, estabelecimento, usuário, senha (hash) | Login do painel |
| Senha | id, estabelecimento, token (UUID), número (ex.: 014), nome do cliente, status, horário de entrada, horário de chamada, aviso de proximidade enviado (sim/não) | Status: aguardando, chamada, finalizada, pulada, cancelada |
| Promoção | id, estabelecimento, título, descrição, preço (decimal), validade (data), ativa (sim/não) | Cliente vê só as ativas e dentro da validade |

**Cálculos**

- **Posição do cliente:** quantas senhas com status "aguardando" têm número menor que o dele, mais um.
- **Aviso de proximidade:** número da senha do cliente menos o número da senha atual é menor ou igual a 3, e o aviso ainda não foi enviado.

## 9. Tecnologias sugeridas

Sugestão de pilha simples para um projeto acadêmico; a equipe pode trocar qualquer item.

| Camada | Sugestão | Por quê |
| --- | --- | --- |
| Back-end | Java com Spring Boot (Spring Web, Spring Data JPA) | Linguagem do curso; uma única aplicação com API e telas |
| Banco de dados | PostgreSQL do Supabase (via pooler) | Banco gerenciado e gratuito; as tabelas ficam num schema próprio (`chamaja`), criado pelo Flyway, fora da API pública do Supabase |
| Tempo real | SSE (Server-Sent Events) do Spring | A fila e os avisos mudam na tela do cliente sem recarregar; o servidor só envia atualizações |
| Login do operador | Spring Security | Controle de acesso do painel, com senha em hash |
| Interface | HTML, CSS e JavaScript servidos pelo Spring (ex.: Thymeleaf), responsivos | Sem instalação: o cliente só abre o link do QR code |
| QR code | Biblioteca ZXing | Gera o código que aponta para a página do estabelecimento |
| Migrações | Flyway | O schema do banco é versionado em arquivos SQL; o Hibernate só confere se bate |
| Avisos no celular | Web Push (VAPID) com a biblioteca `web-push` e um service worker | Notificação com a tela bloqueada, sem app e sem coletar o telefone; funciona em Chrome, Edge, Firefox e Safari (iPhone com o site na Tela de Início) |
| Rotina diária | `@Scheduled` do Spring | Apaga os dados dos clientes e reinicia a numeração ao fim do dia |
| Hospedagem | Railway, com deploy a partir do GitHub | Projeto já usado pela equipe; link público para a demonstração |

Com Java, o login e a atualização em tempo real são implementados no próprio projeto, em vez de vir prontos de um serviço externo.

## 10. Riscos e próximos passos

| Risco | Mitigação |
| --- | --- |
| Cliente pega senha sem estar no local | QR code rotativo numa tela na entrada (o código muda a cada minuto), permissão de uso único ao escanear, uma senha ativa por aparelho e limite de requisições por IP. **Limitação conhecida:** alguém dentro do local pode repassar o QR ao vivo a um amigo, que o usa em até ~2 minutos; sem hardware de presença (como GPS confiável ou Bluetooth), isso não se elimina por completo |
| Cliente sem celular ou sem internet | Manter a senha impressa ou o atendimento tradicional como alternativa |
| Cliente bloqueia a tela e perde o aviso | Web Push: depois de pegar a senha, uma mensagem animada convida a permitir notificações; o aviso chega mesmo com a tela bloqueada. **Limites:** quem recusa a permissão continua só com o aviso na tela; no iPhone só funciona depois de "Adicionar à Tela de Início" (a tela mostra a instrução) |
| Operador esquece de atualizar a fila | Botão único "Chamar próxima" e aviso de senha chamada há muito tempo sem finalizar |
| Dados pessoais do cliente | Pedir só o nome e apagar as senhas ao fim do dia (RNF06) |
| Acesso indevido à senha de outro cliente | Identificar a senha por UUID, nunca por id sequencial (RNF07) |

**Próximos passos**

- [ ] Validar requisitos e telas com o grupo e o professor
- [ ] Criar o protótipo navegável das seis telas, incluindo o aviso de proximidade
- [x] Modelar o banco de dados (entidades JPA; o Hibernate cria as tabelas no PostgreSQL)
- [x] Implementar o fluxo do cliente (RF01 a RF08)
- [x] Implementar o painel do operador (RF09 a RF16)
- [x] Testar com uma fila simulada (26 testes automatizados e um cenário de ponta a ponta com 6 clientes)
- [x] Preparar o deploy: migrações (Flyway), Dockerfile e passo a passo do Supabase e da Railway (README)
- [ ] Criar o projeto no Supabase e o serviço na Railway, e publicar
- [ ] Testar com celulares reais
- [ ] Ajustar o visual com base no protótipo aprovado
