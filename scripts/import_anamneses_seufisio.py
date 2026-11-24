#!/usr/bin/env python3
"""Importa as anamneses do seufisio para a API carlessopilatesapi.

O seufisio não exporta anamneses em lote: cada uma é uma página própria
(`/anamnese-personalizada/ver/{cliente}/{anamnese}`). Os arquivos de entrada
são um JSON por anamnese, extraído dessa página, no formato:

    {"cliente": 8, "anamnese": 1,
     "titulo": "Avaliação Anamnese seufisio - 14/04/2022",
     "campos": [["Objetivo", "..."], ["Queixa Principal", "..."], ...]}

`cliente` é a matrícula do seufisio. O paciente é resolvido pelo XLSX de
clientes (matrícula → e-mail/nome) e casado com a API como no script de datas.

Regras:
- Um paciente tem no máximo uma anamnese na API. Com várias no seufisio, a
  mais recente é a principal e as outras entram em `observacoes`.
- Idempotente: paciente que já tem anamnese na API é pulado.
- Anamnese sem nenhum campo preenchido é pulada.
- Paciente inativo é reativado só para criar e inativado de novo. A API
  grava a data do dia ao inativar, então rode `gerar_sql_datas_seufisio.py`
  depois para restaurar a data real de inativação.

Uso:
    set -a; source scripts/.env; set +a
    python3 scripts/import_anamneses_seufisio.py --xlsx scripts/arquivos/clientes.xlsx \\
        --dir scripts/arquivos/anamneses --dry-run
"""

import argparse
import glob
import json
import os
import re
import sys
import time
from collections import Counter, defaultdict
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from fonte_arquivos_seufisio import clientes_do_xlsx  # noqa: E402
from gerar_sql_datas_seufisio import resolver  # noqa: E402
from import_evolucoes_seufisio import indexar_para_match, patch_paciente  # noqa: E402
from import_seufisio import (  # noqa: E402
    auth_local,
    fetch_pacientes_locais,
    http_json,
    login_local,
    normalize_email,
    short_name,
)

RATE_LIMIT_SECONDS = 0.15
SEM_QUEIXA = "Não informada no seufisio"
SEM_OBJETIVO = "Não informado no seufisio"
# `nivel_atividade_fisica` é VARCHAR(50): vai só a resposta; o "Quais" vai para as observações.
NIVEL_ATIVIDADE = {"sim": "Ativo", "nao": "Sedentário", "passado": "Ativo no passado"}
# Campos do seufisio sem coluna própria: vão para `observacoes`, nesta ordem.
EXTRAS_OBSERVACOES = (
    ("Quais", "Atividades físicas"),
    ("Avaliação Postural", "Avaliação postural"),
    ("Força Muscular", "Força muscular"),
    ("Amplitude do Movimento", "Amplitude do movimento"),
    ("Perimetria/Exames", "Perimetria/exames"),
    ("Histórico Familiar", "Histórico familiar"),
    ("OBS Gerais", "Observações gerais"),
)
_RE_DATA_TITULO = re.compile(r"(\d{2})/(\d{2})/(\d{4})\s*$")


def _chave(txt):
    return (txt or "").strip().lower().replace("ã", "a")


def data_do_titulo(titulo):
    """'Avaliação Anamnese seufisio - 14/04/2022' → '2022-04-14' (ou None)."""
    m = _RE_DATA_TITULO.search(titulo or "")
    return f"{m[3]}-{m[2]}-{m[1]}" if m else None


def _data_br(iso):
    return datetime.strptime(iso, "%Y-%m-%d").strftime("%d/%m/%Y") if iso else "data desconhecida"


def carregar(diretorio):
    """{matrícula: [anamnese, ...]} com as anamneses que têm algum campo preenchido."""
    por_cliente = defaultdict(list)
    vazias = 0
    for caminho in sorted(glob.glob(os.path.join(diretorio, "*.json"))):
        with open(caminho, encoding="utf-8") as f:
            dado = json.load(f)
        campos = {label: (valor or "").strip() for label, valor in dado["campos"]}
        if not any(campos.values()):
            vazias += 1
            continue
        por_cliente[dado["cliente"]].append({
            "id": dado["anamnese"],
            "data": data_do_titulo(dado.get("titulo")),
            "campos": campos,
        })
    return por_cliente, vazias


def _secao(rotulo, texto):
    return f"{rotulo}:\n{texto}" if texto else None


def _juntar(*partes):
    return "\n\n".join(p for p in partes if p) or None


def map_to_anamnese(paciente_id, anamneses):
    """Payload de `POST /anamneses` a partir das anamneses de um cliente."""
    ordenadas = sorted(anamneses, key=lambda a: a["data"] or "", reverse=True)
    principal, anteriores = ordenadas[0], ordenadas[1:]
    c = principal["campos"]

    queixa = _juntar(c.get("Queixa Principal"),
                     _secao("Queixa secundária", c.get("Queixa Secundária")))
    doencas = _juntar(_secao("Diagnóstico médico", c.get("Diagnóstico Médico")),
                      c.get("História Pregressa de Doenças"))
    observacoes = [_secao(rotulo, c.get(label)) for label, rotulo in EXTRAS_OBSERVACOES]
    for anterior in anteriores:
        preenchidos = "\n".join(f"{label}: {valor}" for label, valor in anterior["campos"].items()
                                if valor)
        observacoes.append(_secao(f"Anamnese anterior ({_data_br(anterior['data'])})",
                                  preenchidos))
    observacoes.append(f"Importada do seufisio (anamnese de {_data_br(principal['data'])}).")

    return {
        "pacienteId": paciente_id,
        "queixaPrincipal": queixa or SEM_QUEIXA,
        "historicoDoencas": doencas,
        "historicoCirurgias": None,
        "historicoLesoes": None,
        "medicamentosUso": c.get("Medicamentos em Uso") or None,
        # O seufisio tem um campo só para as duas coisas.
        "alergias": c.get("Alergias/Cirurgias") or None,
        "nivelAtividadeFisica": NIVEL_ATIVIDADE.get(_chave(c.get("Atividades"))),
        "restricoesMedicas": c.get("Restrições") or None,
        "objetivos": c.get("Objetivo") or SEM_OBJETIVO,
        "observacoes": _juntar(*observacoes),
    }


def anamnese_existe(base_url, token, paciente_id):
    status, _ = http_json("GET", f"{base_url}/anamneses/paciente/{paciente_id}",
                          headers=auth_local(token))
    if status == 200:
        return True, None
    if status == 404:
        return False, None
    return None, f"GET /anamneses/paciente/{paciente_id} -> {status}"


def criar(base_url, token, paciente, payload, fails, rotulo):
    """POST /anamneses, reativando o paciente inativo só durante a criação."""
    reativado = False
    if not paciente.get("ativo", True):
        status, _ = patch_paciente(base_url, token, paciente["id"], "ativar")
        if status != 204:
            fails[f"patch_ativar_{status}"] += 1
            print(f"      [fail {rotulo}] PATCH ativar -> {status}")
            return False
        reativado = True
    try:
        status, corpo = http_json("POST", f"{base_url}/anamneses",
                                  headers=auth_local(token), body=payload)
        if status != 201:
            fails[f"post_{status}"] += 1
            print(f"      [fail {rotulo}] POST /anamneses -> {status}: {str(corpo)[:200]}")
            return False
        return True
    finally:
        if reativado:
            status, _ = patch_paciente(base_url, token, paciente["id"], "inativar")
            if status != 204:
                print(f"      [warn {rotulo}] PATCH inativar -> {status} "
                      f"— paciente ficou ATIVO, reverter manualmente")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--xlsx", required=True, metavar="ARQUIVO",
                        help="Relatório de clientes do seufisio (matrícula → paciente).")
    parser.add_argument("--dir", required=True, metavar="PASTA",
                        help="Pasta com um JSON por anamnese.")
    parser.add_argument("--dry-run", action="store_true",
                        help="Não grava: mostra o que seria criado, sem o texto clínico.")
    args = parser.parse_args(argv)

    base_url = os.environ.get("LOCAL_API_URL", "http://localhost:8080").rstrip("/")
    email = os.environ.get("LOCAL_EMAIL")
    senha = os.environ.get("LOCAL_PASSWORD")
    if not email or not senha:
        sys.exit("LOCAL_EMAIL e LOCAL_PASSWORD obrigatórias")

    por_cliente, vazias = carregar(args.dir)
    print(f"[1/3] {sum(len(v) for v in por_cliente.values())} anamneses de "
          f"{len(por_cliente)} clientes ({vazias} vazias ignoradas)")
    clientes = {c["id"]: c for c in clientes_do_xlsx(args.xlsx)}

    print(f"[2/3] Carregando pacientes de {base_url} ...")
    token = login_local(base_url, email, senha)
    pacientes = fetch_pacientes_locais(base_url, token)
    por_cpf, por_nome = indexar_para_match(pacientes)
    por_email = {normalize_email(p.get("email")): p for p in pacientes if p.get("email")}

    print(f"[3/3] {'Simulando' if args.dry_run else 'Importando'} ...")
    ok, skips, fails, reinativados = 0, Counter(), Counter(), 0
    for matricula, anamneses in sorted(por_cliente.items()):
        cliente = clientes.get(matricula)
        if not cliente:
            skips["matricula_fora_do_xlsx"] += 1
            print(f"      [skip seufisio_id={matricula}] matrícula fora do XLSX")
            continue
        paciente, motivo = resolver(cliente, por_email, por_cpf, por_nome)
        if motivo:
            skips[motivo] += 1
            print(f"      [skip seufisio_id={matricula} nome={short_name(cliente['nome'])}] {motivo}")
            continue
        rotulo = f"paciente_id={paciente['id']} nome={short_name(paciente.get('nome'))}"
        existe, erro = anamnese_existe(base_url, token, paciente["id"])
        if erro:
            fails["get_anamnese"] += 1
            print(f"      [fail {rotulo}] {erro}")
            continue
        if existe:
            skips["anamnese_ja_existe"] += 1
            continue
        payload = map_to_anamnese(paciente["id"], anamneses)
        inativo = not paciente.get("ativo", True)
        if args.dry_run:
            tamanhos = {k: len(v) for k, v in payload.items() if isinstance(v, str)}
            print(f"      [dry-run {rotulo}] {len(anamneses)} anamnese(s)"
                  f"{' (inativo: seria reativado)' if inativo else ''} {tamanhos}")
            ok += 1
            continue
        if criar(base_url, token, paciente, payload, fails, rotulo):
            ok += 1
            reinativados += inativo
        time.sleep(RATE_LIMIT_SECONDS)

    print(f"\n{'Seriam criadas' if args.dry_run else 'Criadas'}: {ok}")
    for motivo, n in skips.most_common():
        print(f"  skip {motivo}: {n}")
    for motivo, n in fails.most_common():
        print(f"  fail {motivo}: {n}")
    if reinativados:
        print(f"\n{reinativados} paciente(s) inativo(s) foram reativados e inativados de novo: "
              f"a data de inativação deles virou hoje. Rode gerar_sql_datas_seufisio.py "
              f"e aplique o SQL para restaurar a data real.")


if __name__ == "__main__":
    main()
