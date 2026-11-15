-- Banner da promoção: o mercado pode enviar uma imagem pronta, que o cliente vê no lugar do cartão padrão.

-- Com banner, o preço deixa de ser obrigatório (a arte já traz o preço).
alter table promocao alter column preco drop not null;

-- Dados leves da imagem ficam na própria promoção: a tela do cliente consulta as promoções a cada poucos
-- segundos e não deve carregar o arquivo (nem fazer uma consulta extra) só para saber se há banner.
alter table promocao add column imagem_versao  bigint;    -- instante do envio; muda a cada troca (serve de cache)
alter table promocao add column imagem_largura integer;
alter table promocao add column imagem_altura  integer;

-- O arquivo em si fica separado, para as listagens nunca o carregarem.
create table promocao_imagem (
    promocao_id   bigint primary key references promocao (id) on delete cascade,
    dados         bytea   not null,
    tamanho       integer not null,
    atualizada_em timestamp(6) with time zone not null
);

alter table promocao_imagem enable row level security;
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'anon')
       and exists (select 1 from pg_roles where rolname = 'authenticated') then
        execute format('revoke all on table promocao_imagem from anon, authenticated');
    end if;
end
$$;
