#!/usr/bin/env python3
"""Gera o SQL que grava as datas reais de cadastro e inativação dos pacientes.

A API não aceita `dataCadastro` nem `dataInativacao`: o `PacienteService`
grava a data do dia ao criar e ao inativar. Na importação isso põe todos os
pacientes no mês da carga e infla as séries de novos cadastros e evasão do
dashboard. Este script lê as datas do relatório de clientes do seufisio
(XLSX com as colunas "Cliente desde", "Cadastro no seufisio" e "Data de
Inativação"), casa cada cliente com o paciente da API (e-mail, depois nome
exato único) e escreve um `UPDATE` por paciente para rodar no banco.

Não grava nada na API: só lê os pacientes. O arquivo gerado tem os ids dos
pacientes e deve ficar fora do git (o padrão é `scripts/arquivos/`).

Uso:
    set -a; source scripts/.env; set +a
    python3 scripts/gerar_sql_datas_seufisio.py --xlsx scripts/arquivos/clientes.xlsx
    # revise o .sql e rode no Postgres:  psql "$DATABASE_URL" -f scripts/arquivos/datas-pacientes.sql
"""

import argparse
import os
import sys
from collections import Counter
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from fonte_arquivos_seufisio import SITUACAO_ATIVO, clientes_do_xlsx  # noqa: E402
from import_evolucoes_seufisio import indexar_para_match, resolver_paciente  # noqa: E402
from import_seufisio import (  # noqa: E402
    fetch_pacientes_locais,
    login_local,
    normalize_email,
    short_name,
)

SAIDA_PADRAO = os.path.join(os.path.dirname(os.path.abspath(__file__)), "arquivos",
                            "datas-pacientes.sql")


def data_iso(valor):
    """'DD/MM/AAAA' → 'AAAA-MM-DD'; None quando vazio ou inválido."""
    if not valor:
        return None
    try:
        return datetime.strptime(valor.strip(), "%d/%m/%Y").date().isoformat()
    except ValueError:
        return None


def resolver(cliente, por_email, por_cpf, por_nome):
    """Paciente da API para o cliente do seufisio: e-mail, depois nome exato único."""
    email = normalize_email(cliente.get("email"))
    if email and email in por_email:
        return por_email[email], None
    return resolver_paciente(cliente, por_cpf, por_nome)


def _literal(valor):
    return f"DATE '{valor}'" if valor else "NULL"


def montar_updates(clientes, pacientes):
    """(updates, skips) — um dict por paciente a atualizar e os motivos de skip.

    A data de inativação só é gravada se o paciente também está inativo na
    API; ativo na API zera a data, como faz o `PATCH /ativar`. Situação
    divergente entre os dois lados é reportada e o paciente fica de fora.
    """
    por_cpf, por_nome = indexar_para_match(pacientes)
    por_email = {normalize_email(p.get("email")): p for p in pacientes if p.get("email")}
    updates, skips, vistos = [], Counter(), set()
    for c in clientes:
        paciente, motivo = resolver(c, por_email, por_cpf, por_nome)
        if motivo:
            skips[motivo] += 1
            print(f"      [skip seufisio_id={c.get('id')} nome={short_name(c.get('nome'))}] {motivo}")
            continue
        if paciente["id"] in vistos:
            skips["paciente_repetido"] += 1
            continue
        vistos.add(paciente["id"])
        ativo_sf = c.get("situacao") == SITUACAO_ATIVO
        if bool(paciente.get("ativo")) != ativo_sf:
            skips["situacao_divergente"] += 1
            print(f"      [skip paciente_id={paciente['id']}] situação divergente "
                  f"(seufisio {'ativo' if ativo_sf else 'inativo'})")
            continue
        cadastro = data_iso(c.get("data_cadastro"))
        inativacao = None if ativo_sf else data_iso(c.get("data_inativacao"))
        if not cadastro and not inativacao and ativo_sf:
            skips["sem_datas"] += 1
            continue
        updates.append({"id": paciente["id"], "data_cadastro": cadastro,
                        "data_inativacao": inativacao, "ativo": ativo_sf})
    return updates, skips


def gerar_sql(updates, autor):
    """Script SQL transacional; não sobrescreve data já existente com NULL."""
    autor_sql = (autor or "importacao-seufisio").replace("'", "''")
    linhas = [
        "-- Datas reais de cadastro e inativação vindas do seufisio.",
        "-- Gerado por scripts/gerar_sql_datas_seufisio.py — revise antes de rodar.",
        "BEGIN;",
    ]
    for u in updates:
        sets = []
        if u["data_cadastro"]:
            sets.append(f"data_cadastro = {_literal(u['data_cadastro'])}")
        if not u["ativo"] and u["data_inativacao"]:
            sets.append(f"data_inativacao = {_literal(u['data_inativacao'])}")
        if not sets:
            continue
        sets += ["version = version + 1", "data_atualizacao = now()",
                 f"alterado_por = '{autor_sql}'"]
        cond = "ativo" if u["ativo"] else "NOT ativo"
        linhas.append(f"UPDATE pacientes SET {', '.join(sets)} WHERE id = {u['id']} AND {cond};")
    linhas.append("COMMIT;")
    return "\n".join(linhas) + "\n"


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--xlsx", required=True, metavar="ARQUIVO",
                        help="Relatório de clientes do seufisio com as colunas de data.")
    parser.add_argument("--saida", default=SAIDA_PADRAO, metavar="ARQUIVO",
                        help=f"Onde gravar o SQL (padrão: {SAIDA_PADRAO}).")
    args = parser.parse_args(argv)

    base_url = os.environ.get("LOCAL_API_URL", "http://localhost:8080").rstrip("/")
    email = os.environ.get("LOCAL_EMAIL")
    senha = os.environ.get("LOCAL_PASSWORD")
    if not email or not senha:
        sys.exit("LOCAL_EMAIL e LOCAL_PASSWORD obrigatórias")

    clientes = clientes_do_xlsx(args.xlsx)
    if not any(c.get("data_cadastro") for c in clientes):
        sys.exit("O XLSX não tem as colunas \"Cliente desde\"/\"Cadastro no seufisio\"")
    print(f"[1/3] {len(clientes)} clientes no XLSX")

    print(f"[2/3] Carregando pacientes de {base_url} ...")
    pacientes = fetch_pacientes_locais(base_url, login_local(base_url, email, senha))
    print(f"      {len(pacientes)} pacientes")

    print("[3/3] Casando clientes e gerando o SQL ...")
    updates, skips = montar_updates(clientes, pacientes)
    with open(args.saida, "w", encoding="utf-8") as f:
        f.write(gerar_sql(updates, email))
    print(f"\n{len(updates)} UPDATEs em {args.saida}")
    print(f"  com data de cadastro:   {sum(1 for u in updates if u['data_cadastro'])}")
    print(f"  com data de inativação: {sum(1 for u in updates if u['data_inativacao'])}")
    for motivo, n in skips.most_common():
        print(f"  skip {motivo}: {n}")


if __name__ == "__main__":
    main()
