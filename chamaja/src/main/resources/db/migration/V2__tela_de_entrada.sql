-- Tela de entrada: cada estabelecimento ganha um link secreto próprio (/tela/{codigo_tela}) para o
-- tablet da entrada, que mostra o QR code rotativo sem exigir login do operador.

alter table estabelecimento add column codigo_tela varchar(40);

-- Estabelecimentos que já existem recebem um código aleatório (gen_random_uuid usa gerador seguro).
update estabelecimento
   set codigo_tela = replace(gen_random_uuid()::text, '-', '')
 where codigo_tela is null;

alter table estabelecimento alter column codigo_tela set not null;
alter table estabelecimento add constraint uk_estabelecimento_codigo_tela unique (codigo_tela);
