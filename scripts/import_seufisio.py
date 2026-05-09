#!/usr/bin/env python3
"""
Importa pacientes do seufisio.com.br para a API local carlessopilatesapi.

Pré-requisitos:
- API local rodando em LOCAL_API_URL (default http://localhost:8080).
- Banco dev limpo (seed V2 já foi removida; rodar `docker compose down -v`
  antes de subir o ambiente caso já existisse a carga anterior).
- Token JWT válido do seufisio (header `authorization` capturado no DevTools).

Uso:
    export SEUFISIO_TOKEN="eyJ..."           # JWT capturado no DevTools
    export SEUFISIO_CLINICA_ID="..."         # header `setfisio` (DevTools)
    export SEUFISIO_VERSION_APP="..."        # header `x-version-app` (DevTools)
    export LOCAL_API_URL="http://localhost:8080"
    export LOCAL_EMAIL="..."
    export LOCAL_PASSWORD="..."
    python3 scripts/import_seufisio.py [--dry-run]

--dry-run: imprime os payloads que seriam enviados, sem POSTar.

Segurança:
- Tokens são lidos apenas de variáveis de ambiente; nunca commitar credenciais.
- O script não persiste em disco nenhum dado vindo do seufisio.
"""

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

SEUFISIO_BASE = "https://api.seufisio.com.br/api"
SITUACAO_ATIVO = 2
RATE_LIMIT_SECONDS = 0.15
BROWSER_UA = (
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"
)


def http_json(method, url, headers=None, body=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Accept", "application/json")
    req.add_header("User-Agent", BROWSER_UA)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read().decode("utf-8")
            return resp.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        return e.code, raw


def seufisio_headers(token, clinica_id, version_app):
    return {
        "Authorization": f"Bearer {token}",
        "acesso": "web-mobile",
        "x-version-app": version_app,
        "setfisio": clinica_id,
        "Origin": "https://app.seufisio.com.br",
        "Referer": "https://app.seufisio.com.br/",
        "X-Requested-With": "XMLHttpRequest",
    }


def login_local(base_url, email, password):
    status, body = http_json(
        "POST", f"{base_url}/auth/login",
        body={"email": email, "password": password},
    )
    if status != 200 or not isinstance(body, dict):
        raise SystemExit(f"Login local falhou ({status}): {body}")
    return body["accessToken"]


def list_seufisio(headers):
    status, body = http_json(
        "GET", f"{SEUFISIO_BASE}/cliente?per_page=500",
        headers=headers,
    )
    if status != 200 or not isinstance(body, dict):
        raise SystemExit(f"Listagem seufisio falhou ({status}): {body}")
    return body.get("data", [])


def fetch_seufisio(headers, cid):
    status, body = http_json(
        "GET", f"{SEUFISIO_BASE}/cliente/{cid}",
        headers=headers,
    )
    if status != 200:
        return None, f"GET cliente/{cid} -> {status}"
    return body, None


def map_to_paciente(c):
    nome = (c.get("nome") or "").strip() or None
    if not nome:
        return None, "sem nome"

    email = (c.get("email") or "").strip() or None
    cpf_raw = c.get("cpf")
    cpf = re.sub(r"\D", "", cpf_raw) if cpf_raw else None
    telefone = (c.get("telefone") or "").strip() or None
    data_nascimento = c.get("data_nascimento") or None

    cep_raw = c.get("cep")
    cep = re.sub(r"\D", "", cep_raw) if cep_raw else None
    endereco_campos = {
        "logradouro": c.get("endereco"),
        "numero": c.get("endereco_numero"),
        "bairro": c.get("bairro"),
        "cidade": c.get("cidade"),
        "uf": c.get("uf"),
        "cep": cep,
    }
    endereco = endereco_campos if any(endereco_campos.values()) else None

    return {
        "nome": nome,
        "email": email,
        "cpf": cpf,
        "telefone": telefone,
        "dataNascimento": data_nascimento,
        "endereco": endereco,
    }, None


def post_paciente(base_url, token, payload):
    return http_json(
        "POST", f"{base_url}/pacientes",
        headers={"Authorization": f"Bearer {token}"},
        body=payload,
    )


def patch_inativar(base_url, token, paciente_id):
    return http_json(
        "PATCH", f"{base_url}/pacientes/{paciente_id}/inativar",
        headers={"Authorization": f"Bearer {token}"},
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--dry-run", action="store_true",
                        help="Imprime payloads sem chamar a API local.")
    args = parser.parse_args()

    seufisio_token = os.environ.get("SEUFISIO_TOKEN")
    clinica_id = os.environ.get("SEUFISIO_CLINICA_ID")
    version_app = os.environ.get("SEUFISIO_VERSION_APP")
    base_url = os.environ.get("LOCAL_API_URL", "http://localhost:8080").rstrip("/")
    local_email = os.environ.get("LOCAL_EMAIL")
    local_password = os.environ.get("LOCAL_PASSWORD")

    if not seufisio_token:
        sys.exit("Variável SEUFISIO_TOKEN obrigatória")
    if not clinica_id or not version_app:
        sys.exit("SEUFISIO_CLINICA_ID e SEUFISIO_VERSION_APP obrigatórias "
                 "(headers `setfisio` e `x-version-app` capturados no DevTools)")
    if not args.dry_run and (not local_email or not local_password):
        sys.exit("LOCAL_EMAIL e LOCAL_PASSWORD obrigatórias (use --dry-run para testar mapeamento)")

    sf_headers = seufisio_headers(seufisio_token, clinica_id, version_app)

    print(f"[1/3] Listando clientes em {SEUFISIO_BASE}/cliente ...")
    clientes = list_seufisio(sf_headers)
    print(f"      {len(clientes)} clientes encontrados")

    print("[2/3] Buscando detalhes ...")
    detalhes = []
    for c in clientes:
        cid = c["id"]
        full, err = fetch_seufisio(sf_headers, cid)
        if err:
            print(f"      [skip {cid}] {err}")
            continue
        detalhes.append(full)
        time.sleep(RATE_LIMIT_SECONDS)

    local_token = None if args.dry_run else login_local(base_url, local_email, local_password)

    print(f"[3/3] {'Simulando' if args.dry_run else 'Enviando'} POST /pacientes ...")
    ok = fail = 0
    for c in detalhes:
        payload, skip = map_to_paciente(c)
        if skip:
            print(f"      [skip id={c.get('id')}] {skip}")
            continue
        if args.dry_run:
            print(json.dumps(payload, ensure_ascii=False))
            ok += 1
            continue

        status, body = post_paciente(base_url, local_token, payload)
        if status != 201 or not isinstance(body, dict):
            fail += 1
            print(f"      [fail id={c.get('id')} nome={payload['nome']!r}] {status}: {body}")
            continue
        novo_id = body["id"]
        ok += 1

        if c.get("situacao") != SITUACAO_ATIVO:
            inat_status, inat_body = patch_inativar(base_url, local_token, novo_id)
            if inat_status != 204:
                print(f"      [warn inativar id={novo_id}] {inat_status}: {inat_body}")

    print(f"Concluído: ok={ok} fail={fail}")
    if fail:
        sys.exit(1)


if __name__ == "__main__":
    main()
