#!/usr/bin/env python3
"""Migra os planos (pacotes fixos) e as faturas do seufisio para a API.

Entradas (exportações da interface do seufisio, em `scripts/arquivos/`):
- `--clientes`: Relatórios → Clientes (XLSX), para casar matrícula → paciente.
- `--planos`: Relatórios → Clientes → aba "Planos Fixos" (XLSX), com
  Periodicidade, Valor, Início/Fim e Horários Programados marcados.
- `--contas`: Relatórios → Financeiros → Contas a receber (XLSX).
- `--pagas`: arquivo texto com `Nº:DD/MM/AAAA:P|D` por fatura paga (data de
  pagamento e forma, PIX ou dinheiro), que a exportação XLSX não traz.

Para cada cliente com pacote fixo vigente ou já vendido:
1. Cria um Plano MENSAL com os dias e o horário do pacote. A API aceita um
   horário só por plano: vale o do primeiro dia, e as aulas dos outros dias
   são remarcadas para o horário certo no passo 4.
2. Cria um Pagamento por fatura: as em aberto (inclusive as vencidas) e as
   pagas de pacotes vigentes ou futuros. Os períodos seguem o ciclo mensal da
   API (início + 1 mês - 1 dia), encadeados a partir do primeiro pacote, para
   que o scheduler de cobranças continue a sequência sem duplicar.
3. Confirma como PAGO as faturas pagas no seufisio, com a data e a forma de
   lá. A confirmação gera as aulas do período.
4. Ajusta as aulas: horário do dia, profissional do horário (quando cadastrado
   na API) e, para aulas passadas, marca como realizada quando há sessão com
   evolução importada naquela data.

Idempotente: cliente que já tem plano na API é pulado nos passos 1 a 3; o
passo 4 roda sempre sobre as aulas dos planos importados, então dá para
reexecutar depois de cadastrar as profissionais.

Uso:
    set -a; source scripts/.env; set +a
    python3 scripts/import_planos_seufisio.py --clientes ... --planos ... \\
        --contas ... --pagas ... --dry-run
"""

import argparse
import os
import re
import sys
import time
from collections import Counter, defaultdict
from datetime import date, datetime, timedelta

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from fonte_arquivos_seufisio import _ler_planilha, clientes_do_xlsx  # noqa: E402
from gerar_sql_datas_seufisio import resolver  # noqa: E402
from import_evolucoes_seufisio import (  # noqa: E402
    indexar_para_match,
    normalizar_nome,
    sessoes_existentes,
)
from import_seufisio import (  # noqa: E402
    auth_local,
    fetch_pacientes_locais,
    http_json,
    login_local,
    normalize_email,
    short_name,
)

RATE_LIMIT_SECONDS = 0.15
DIAS = {"segunda": "MONDAY", "terca": "TUESDAY", "quarta": "WEDNESDAY", "quinta": "THURSDAY",
        "sexta": "FRIDAY", "sabado": "SATURDAY", "domingo": "SUNDAY"}
DIAS_JAVA = ("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
FREQUENCIA = {1: "UMA_VEZ", 2: "DUAS_VEZES", 3: "TRES_VEZES"}
FORMA = {"P": "PIX", "D": "DINHEIRO"}
_RE_HORARIO = re.compile(r"(\w+)\s+às\s+(\d{2}:\d{2})\s+com\s+(.+?)\s+na sala", re.IGNORECASE)


def _data(txt):
    return datetime.strptime(txt.strip(), "%d/%m/%Y").date()


def _valor(txt):
    return round(float(txt.replace(".", "").replace(",", ".")), 2)


def _mais_um_mes(d):
    """Mesmo cálculo da API: `plusMonths(1).minusDays(1)` a partir de `d`."""
    ano, mes = (d.year, d.month + 1) if d.month < 12 else (d.year + 1, 1)
    dia = min(d.day, [31, 29 if ano % 4 == 0 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][mes - 1])
    return date(ano, mes, dia) - timedelta(days=1)


def horarios(texto):
    """'Terça às 09:00 com CAMILA… na sala Sala 01, …' → [(DayOfWeek, 'HH:MM', profissional)]."""
    saida = []
    for dia, hora, prof in _RE_HORARIO.findall((texto or "").replace("\xa0", " ")):
        chave = normalizar_nome(dia)
        if chave in DIAS:
            saida.append((DIAS[chave], hora, prof.strip()))
    return saida


def ler_pagas(caminho):
    """{Nº da fatura: (data_pagamento ISO, forma)}."""
    pagas = {}
    with open(caminho, encoding="utf-8") as f:
        for item in f.read().split():
            numero, data_pag, forma = item.split(":")
            pagas[numero] = (_data(data_pag).isoformat(), FORMA.get(forma))
    return pagas


def montar(planos_xlsx, contas_xlsx, pagas, hoje):
    """{matrícula: {"pacotes": [...], "faturas": [...]}} com o que será importado."""
    pacotes = defaultdict(list)
    nome_para_mat = {}
    for linha in _ler_planilha(planos_xlsx):
        if not linha.get("Mat.", "").isdigit():
            continue
        mat = int(linha["Mat."])
        nome_para_mat[normalizar_nome(linha["Cliente"])] = mat
        pacotes[mat].append({
            "plano": linha["Plano"],
            "valor": _valor(linha["Valor"]),
            "inicio": _data(linha["Data início plano"]),
            "fim": _data(linha["Data fim plano"]),
            "horarios": horarios(linha["Horários Programados"]),
            "periodicidade": linha.get("Periodicidade"),
        })
    faturas = defaultdict(list)
    sem_plano = []
    for linha in _ler_planilha(contas_xlsx):
        if not linha.get("Nº", "").isdigit():
            continue
        mat = nome_para_mat.get(normalizar_nome(linha["Cliente"]))
        if mat is None:
            sem_plano.append(linha["Nº"])
            continue
        numero = linha["Nº"]
        paga = numero in pagas
        vencimento = _data(linha["Data Venc."])
        # Paga de pacote já encerrado é histórico: não entra. Em aberto entra sempre.
        if paga and not any(p["fim"] >= hoje and abs((vencimento - p["inicio"]).days) <= 15
                            for p in pacotes[mat]):
            continue
        faturas[mat].append({
            "numero": numero,
            "valor": _valor(linha["Valor"]),
            "vencimento": vencimento,
            "paga": pagas.get(numero),
        })
    for mat in faturas:
        faturas[mat].sort(key=lambda f: f["vencimento"])
    for mat in pacotes:
        pacotes[mat].sort(key=lambda p: p["inicio"])
    return {m: {"pacotes": pacotes[m], "faturas": faturas.get(m, [])} for m in pacotes}, sem_plano


def plano_payload(paciente_id, dados):
    """Payload de `POST /planos` a partir dos pacotes do cliente."""
    pacotes = dados["pacotes"]
    base = pacotes[-1]  # o mais recente define os horários vigentes
    dias = [d for d, _, _ in base["horarios"]]
    valores = [p["valor"] for p in pacotes] + [f["valor"] for f in dados["faturas"]]
    primeiro = min([p["inicio"] for p in pacotes] + [f["vencimento"] for f in dados["faturas"]])
    return {
        "pacienteId": paciente_id,
        "tipo": "MENSAL",
        # O pagamento não pode ser menor que o plano: o plano fica com o menor valor.
        "valor": min(valores),
        "frequenciaSemanal": FREQUENCIA.get(len(dias)),
        "diasSemana": dias,
        "dataInicio": primeiro.isoformat(),
        "horario": f"{base['horarios'][0][1]}:00" if base["horarios"] else None,
    }


def _somar_meses(d, meses):
    total = d.year * 12 + d.month - 1 + meses
    ano, mes = divmod(total, 12)
    ultimo = _mais_um_mes(date(ano, mes + 1, 1)).day
    return date(ano, mes + 1, min(d.day, ultimo))


def ciclos(dados, hoje):
    """[(fatura, periodo_inicio)] em ciclos mensais ancorados no pacote vigente.

    A fatura mais próxima do início do pacote vigente abre o ciclo dele; as
    anteriores (vencidas) e as seguintes (já vendidas) ocupam os meses antes e
    depois, para que o período de hoje coincida com o pacote de hoje.
    """
    faturas = dados["faturas"]
    if not faturas:
        return []
    vigentes = [p for p in dados["pacotes"] if p["inicio"] <= hoje <= p["fim"]] or dados["pacotes"]
    ancora = vigentes[0]["inicio"]
    k = min(range(len(faturas)), key=lambda i: abs((faturas[i]["vencimento"] - ancora).days))
    saida, usados = [], set()
    for i, fatura in enumerate(faturas):
        # Fatura de um pacote conhecido usa o início dele (cobre pacotes bimestrais, uma fatura
        # a cada dois meses); a vencida sem pacote na exportação conta meses a partir da âncora.
        pacote = next((p for p in dados["pacotes"]
                       if abs((fatura["vencimento"] - p["inicio"]).days) <= 15), None)
        inicio = pacote["inicio"] if pacote else _somar_meses(ancora, i - k)
        while inicio in usados:
            inicio = _somar_meses(inicio, 1)
        usados.add(inicio)
        saida.append((fatura, inicio))
    return saida


def _get(base, token, caminho):
    return http_json("GET", f"{base}{caminho}", headers=auth_local(token))


def profissionais_por_nome(base, token):
    # Sem a lista, o passo 4 trataria todas as aulas como sem profissional: melhor parar.
    for _ in range(3):
        status, corpo = _get(base, token, "/profissionais?size=100")
        if status == 200 and isinstance(corpo, dict):
            return {normalizar_nome(p["nome"]): p["id"] for p in corpo.get("content", [])
                    if p.get("ativo", True)}
        time.sleep(2)
    raise SystemExit(f"Falha ao listar profissionais ({status}): {str(corpo)[:200]}")


def ajustar_aulas(base, token, paciente_id, plano_id, horarios_dia, profs, hoje, dry, conta):
    """Horário do dia, profissional e realização das aulas passadas com sessão."""
    status, pagamentos = _get(base, token, f"/pagamentos/paciente/{paciente_id}?size=100")
    pagamentos = pagamentos.get("content", []) if isinstance(pagamentos, dict) else []
    aulas = []
    for pg in pagamentos:
        if pg["planoId"] == plano_id and pg["status"] == "PAGO":
            st, lista = _get(base, token, f"/aulas/pagamento/{pg['id']}")
            aulas += lista if st == 200 and isinstance(lista, list) else []
    sessoes, _ = sessoes_existentes(base, token, paciente_id)
    dias_com_sessao = {chave[:10] for chave in (sessoes or {})}
    for aula in aulas:
        dia = datetime.strptime(aula["data"], "%Y-%m-%d").date()
        hora, prof = horarios_dia.get(DIAS_JAVA[dia.weekday()], (None, None))
        prof_id = profs.get(normalizar_nome(prof)) if prof else None
        if hora and (aula.get("horario") or "")[:5] != hora:
            conta["horario_ajustado"] += 1
            if not dry:
                http_json("PATCH", f"{base}/aulas/{aula['id']}/remarcar", headers=auth_local(token),
                          body={"data": aula["data"], "horario": f"{hora}:00"})
        if aula["realizada"]:
            continue
        if dia < hoje:
            if aula["data"] in dias_com_sessao and prof_id:
                conta["realizada"] += 1
                if not dry:
                    http_json("PATCH", f"{base}/aulas/{aula['id']}/realizar?profissionalId={prof_id}",
                              headers=auth_local(token))
            elif aula["data"] in dias_com_sessao:
                conta["realizada_aguarda_profissional"] += 1
            else:
                conta["passada_sem_sessao"] += 1
            continue
        if prof_id and aula.get("profissionalId") != prof_id:
            conta["profissional_vinculado"] += 1
            if not dry:
                st, corpo = http_json("PATCH", f"{base}/aulas/{aula['id']}/profissional",
                                      headers=auth_local(token), body={"profissionalId": prof_id})
                if st != 200:
                    conta[f"profissional_{st}"] += 1
        elif not prof_id:
            conta["futura_sem_profissional"] += 1


def confirmar_abertos(base, token, paciente_id, plano_id, dry, conta, rotulo):
    """Confirma os pagamentos não pagos do plano, com o vencimento como data de pagamento."""
    st, corpo = _get(base, token, f"/pagamentos/paciente/{paciente_id}?size=100")
    for pg in (corpo.get("content", []) if isinstance(corpo, dict) else []):
        if pg["planoId"] != plano_id or pg["status"] == "PAGO":
            continue
        conta["abertos_confirmados"] += 1
        if dry:
            continue
        st, _ = http_json("PATCH", f"{base}/pagamentos/{pg['id']}/pagar", headers=auth_local(token),
                          body={"dataPagamento": pg["dataVencimento"]})
        if st != 200:
            conta[f"fail_pagar_{st}"] += 1
            print(f"      [fail {rotulo}] PATCH pagar pagamento {pg['id']} -> {st}")
        time.sleep(RATE_LIMIT_SECONDS)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    for nome in ("clientes", "planos", "contas", "pagas"):
        parser.add_argument(f"--{nome}", required=True, metavar="ARQUIVO")
    parser.add_argument("--hoje", default=date.today().isoformat(), metavar="AAAA-MM-DD")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--abertas-como-pagas", action="store_true",
                        help="Confirma também as faturas em aberto, com o vencimento como data de pagamento "
                             "e sem forma. Para quando o seufisio não era mantido em dia, e não havia "
                             "inadimplência de fato. Vale também para planos já importados.")
    args = parser.parse_args(argv)
    hoje = date.fromisoformat(args.hoje)

    base = os.environ.get("LOCAL_API_URL", "http://localhost:8080").rstrip("/")
    email, senha = os.environ.get("LOCAL_EMAIL"), os.environ.get("LOCAL_PASSWORD")
    if not email or not senha:
        sys.exit("LOCAL_EMAIL e LOCAL_PASSWORD obrigatórias")

    pagas = ler_pagas(args.pagas)
    por_cliente, sem_plano = montar(args.planos, args.contas, pagas, hoje)
    clientes = {c["id"]: c for c in clientes_do_xlsx(args.clientes)}
    print(f"[1/3] {len(por_cliente)} clientes com pacote; "
          f"{sum(len(d['faturas']) for d in por_cliente.values())} faturas a importar; "
          f"{len(sem_plano)} faturas de clientes sem pacote vigente ficam de fora")

    token = login_local(base, email, senha)
    pacientes = fetch_pacientes_locais(base, token)
    por_cpf, por_nome = indexar_para_match(pacientes)
    por_email = {normalize_email(p.get("email")): p for p in pacientes if p.get("email")}
    profs = profissionais_por_nome(base, token)
    print(f"[2/3] {len(pacientes)} pacientes e {len(profs)} profissionais na API")

    print(f"[3/3] {'Simulando' if args.dry_run else 'Migrando'} ...")
    conta, avisos = Counter(), []
    for mat, dados in sorted(por_cliente.items()):
        cliente = clientes.get(mat)
        paciente, motivo = resolver(cliente, por_email, por_cpf, por_nome) if cliente else (None, "fora_do_xlsx")
        if motivo:
            conta[f"skip_{motivo}"] += 1
            print(f"      [skip seufisio_id={mat}] {motivo}")
            continue
        rotulo = f"paciente_id={paciente['id']} nome={short_name(paciente.get('nome'))}"
        if not paciente.get("ativo"):
            conta["skip_paciente_inativo"] += 1
            print(f"      [skip {rotulo}] paciente inativo na API")
            continue
        payload = plano_payload(paciente["id"], dados)
        base_h = dados["pacotes"][-1]["horarios"]
        horarios_dia = {d: (h, p) for d, h, p in base_h}
        if len({h for _, h, _ in base_h}) > 1:
            avisos.append(f"{rotulo}: horários diferentes por dia — aulas remarcadas por dia")
        if any(p["periodicidade"] != "Mensal" for p in dados["pacotes"]):
            avisos.append(f"{rotulo}: pacote não mensal no seufisio — importado como MENSAL")
        if not payload["frequenciaSemanal"]:
            conta["skip_sem_horarios"] += 1
            print(f"      [skip {rotulo}] pacote sem horários programados")
            continue

        st, existentes = _get(base, token, f"/planos/paciente/{paciente['id']}")
        plano = next((p for p in (existentes or []) if p.get("ativo")), None) if st == 200 else None
        if plano is None:
            if args.dry_run:
                plano = {"id": None}
            else:
                st, plano = http_json("POST", f"{base}/planos", headers=auth_local(token), body=payload)
                if st != 201:
                    conta[f"fail_plano_{st}"] += 1
                    print(f"      [fail {rotulo}] POST /planos -> {st}: {str(plano)[:200]}")
                    continue
            conta["planos"] += 1
            for fatura, inicio in ciclos(dados, hoje):
                if not fatura["paga"] and args.abertas_como_pagas:
                    fatura = dict(fatura, paga=(fatura["vencimento"].isoformat(), None))
                conta["pagamentos"] += 1
                conta["pagos" if fatura["paga"] else "em_aberto"] += 1
                if args.dry_run:
                    continue
                st, pg = http_json("POST", f"{base}/pagamentos", headers=auth_local(token), body={
                    "pacienteId": paciente["id"], "planoId": plano["id"], "valor": fatura["valor"],
                    "dataVencimento": fatura["vencimento"].isoformat(),
                    "periodoInicio": inicio.isoformat()})
                if st != 201:
                    conta[f"fail_pagamento_{st}"] += 1
                    print(f"      [fail {rotulo}] POST /pagamentos fatura {fatura['numero']} -> {st}: "
                          f"{str(pg)[:200]}")
                    continue
                if fatura["paga"]:
                    data_pag, forma = fatura["paga"]
                    if forma:  # pago de fato no seufisio: data futura vira hoje
                        data_pag = min(data_pag, hoje.isoformat())
                    st, _ = http_json("PATCH", f"{base}/pagamentos/{pg['id']}/pagar",
                                      headers=auth_local(token),
                                      body={"dataPagamento": data_pag, "formaPagamento": forma})
                    if st != 200:
                        conta[f"fail_pagar_{st}"] += 1
                        print(f"      [fail {rotulo}] PATCH pagar -> {st}")
                time.sleep(RATE_LIMIT_SECONDS)
        else:
            conta["planos_ja_existentes"] += 1
            if args.abertas_como_pagas:
                confirmar_abertos(base, token, paciente["id"], plano["id"], args.dry_run, conta, rotulo)
        if plano.get("id"):
            ajustar_aulas(base, token, paciente["id"], plano["id"], horarios_dia, profs, hoje,
                          args.dry_run, conta)

    print()
    for chave, n in sorted(conta.items()):
        print(f"  {chave}: {n}")
    for aviso in avisos:
        print(f"  aviso {aviso}")


if __name__ == "__main__":
    main()
