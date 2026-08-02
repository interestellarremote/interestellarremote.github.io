from __future__ import annotations

import asyncio
import hashlib
import io
import json
import os
import time
from dataclasses import dataclass
from typing import Any

import qrcode
from fastapi import FastAPI, HTTPException, Response
from fastapi.responses import HTMLResponse

from .agent_runtime import find_agy_cli
from .config import Settings
from .crypto import b64e
from .database import Database
from .firebase_transport import FirebaseTransport
from .filesystem_access import (
    browse_directories,
    create_directory,
    create_project_directory,
    resolve_local_directory,
)
from .models import Project
from .secrets import SecretStore


PAGE = """<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Interestellar Remote Bridge</title><style>
:root{
color-scheme:dark;
--bg:#050816;
--text:#eef3ff;
--muted:#9ba7c7;
--teal:#37e3da;
--violet:#8d5bff;
--ok:#7af0b0;
--line:rgba(138,164,255,.16);
--line-strong:rgba(117,241,231,.28);
--shadow:0 18px 60px rgba(0,0,0,.38);
font-family:Inter,Segoe UI,Roboto,system-ui,sans-serif;
}
*{box-sizing:border-box}
body{
margin:0;
min-height:100vh;
background:
radial-gradient(circle at 15% 15%, rgba(55,227,218,.18), transparent 24%),
radial-gradient(circle at 85% 20%, rgba(141,91,255,.18), transparent 28%),
radial-gradient(circle at 50% 120%, rgba(27,82,255,.18), transparent 38%),
linear-gradient(180deg, #040714 0%, #08101c 38%, #040713 100%);
color:var(--text);
}
body::before{
content:"";
position:fixed;
inset:0;
pointer-events:none;
background-image:
radial-gradient(circle at 20% 30%, rgba(255,255,255,.9) 0 1px, transparent 2px),
radial-gradient(circle at 75% 18%, rgba(117,241,231,.8) 0 1px, transparent 2px),
radial-gradient(circle at 68% 72%, rgba(255,255,255,.7) 0 1px, transparent 2px),
radial-gradient(circle at 14% 82%, rgba(141,91,255,.8) 0 1px, transparent 2px);
opacity:.42;
}
.shell{
width:min(1180px,calc(100vw - 32px));
margin:28px auto 42px;
position:relative;
z-index:1;
}
.hero,.card{
position:relative;
overflow:hidden;
border-radius:28px;
border:1px solid var(--line);
background:linear-gradient(180deg, rgba(13,20,38,.94), rgba(9,14,28,.94));
box-shadow:var(--shadow);
}
.hero{
padding:28px;
background:
linear-gradient(145deg, rgba(12,20,38,.96), rgba(9,14,30,.88)),
radial-gradient(circle at 20% 20%, rgba(55,227,218,.18), transparent 28%),
radial-gradient(circle at 76% 24%, rgba(141,91,255,.2), transparent 32%);
}
.hero-grid{
display:grid;
grid-template-columns:minmax(0,1.3fr) minmax(300px,.9fr);
gap:22px;
align-items:stretch;
}
.eyebrow{
display:inline-flex;
align-items:center;
gap:10px;
padding:7px 12px;
border:1px solid rgba(117,241,231,.22);
border-radius:999px;
background:rgba(14,24,44,.72);
font-size:12px;
letter-spacing:.18em;
text-transform:uppercase;
color:var(--teal);
}
.dot{
width:8px;
height:8px;
border-radius:999px;
background:linear-gradient(180deg,var(--teal),#7cf4ff);
box-shadow:0 0 18px rgba(55,227,218,.65);
}
h1{
margin:18px 0 12px;
font-size:clamp(2.2rem,4vw,4.1rem);
line-height:.95;
letter-spacing:-.05em;
}
.sub{
max-width:62ch;
margin:0;
font-size:1.03rem;
line-height:1.75;
color:#c6d1eb;
}
.hero-tags,.actions,.toolbar{
display:flex;
flex-wrap:wrap;
gap:10px;
}
.hero-tags{margin-top:22px}
.tag{
padding:10px 14px;
border-radius:999px;
border:1px solid rgba(255,255,255,.08);
background:rgba(255,255,255,.04);
font-size:.92rem;
color:#dbe6ff;
}
.hero-card{
padding:22px;
border-radius:24px;
border:1px solid rgba(117,241,231,.18);
background:
linear-gradient(180deg, rgba(14,22,42,.88), rgba(10,15,28,.94)),
radial-gradient(circle at 60% 8%, rgba(55,227,218,.12), transparent 36%);
}
.hero-card h2{margin:0 0 10px;font-size:1.02rem;color:#dffefc}
.hero-card p{margin:0;color:var(--muted);line-height:1.65}
.status-pill{
display:inline-flex;
align-items:center;
gap:8px;
margin-bottom:16px;
padding:9px 13px;
border-radius:999px;
border:1px solid rgba(122,240,176,.2);
background:rgba(122,240,176,.08);
color:#dffcea;
font-size:.92rem;
}
.layout{
display:grid;
grid-template-columns:minmax(0,1fr) minmax(340px,.82fr);
gap:22px;
margin-top:22px;
}
.stack{display:grid;gap:22px}
.card{padding:22px}
.section-head{
display:flex;
justify-content:space-between;
align-items:flex-start;
gap:16px;
margin-bottom:16px;
}
.section-head p{margin:8px 0 0;color:var(--muted);line-height:1.6}
.grid-two{
display:grid;
grid-template-columns:repeat(2,minmax(0,1fr));
gap:14px;
}
.metric,.list-card,.checkbox-row,.qr-shell,.helper,.current-path{
border-radius:20px;
background:rgba(255,255,255,.035);
border:1px solid rgba(255,255,255,.06);
}
.metric{padding:16px}
.metric .label{
display:block;
margin-bottom:8px;
font-size:.82rem;
text-transform:uppercase;
letter-spacing:.12em;
color:var(--muted);
}
.metric strong{
display:block;
font-size:1.08rem;
line-height:1.45;
overflow-wrap:anywhere;
word-break:break-word;
max-width:100%;
}
.metric .value-id{
font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;
font-size:.96rem;
letter-spacing:.01em;
}
.metric .value-success{
font-size:1rem;
}
.qr-shell{
display:grid;
gap:14px;
justify-items:center;
padding:18px;
margin-top:16px;
}
.qr-frame{
width:min(100%,280px);
aspect-ratio:1/1;
display:grid;
place-items:center;
border-radius:28px;
border:1px dashed rgba(117,241,231,.28);
background:
radial-gradient(circle at 50% 10%, rgba(55,227,218,.08), transparent 45%),
linear-gradient(180deg, rgba(6,11,23,.95), rgba(8,16,28,.95));
}
#qr{max-width:100%;height:auto;border-radius:18px;box-shadow:0 20px 45px rgba(0,0,0,.28)}
button,input{font:inherit}
button{
appearance:none;
border:1px solid rgba(255,255,255,.12);
background:linear-gradient(180deg, #18243d, #10192c);
color:var(--text);
padding:11px 16px;
border-radius:14px;
cursor:pointer;
transition:transform .18s ease, border-color .18s ease, background .18s ease;
}
button:hover{transform:translateY(-1px);border-color:rgba(117,241,231,.34)}
button.primary{
background:linear-gradient(135deg, var(--teal), #c586ff);
color:#061019;
border-color:transparent;
font-weight:700;
}
button.subtle{background:rgba(255,255,255,.04)}
button:disabled{opacity:.45;cursor:not-allowed;transform:none}
input{
width:100%;
padding:13px 14px;
border-radius:16px;
border:1px solid rgba(255,255,255,.1);
background:rgba(3,8,18,.72);
color:var(--text);
outline:none;
}
input:focus{
border-color:rgba(117,241,231,.4);
box-shadow:0 0 0 4px rgba(55,227,218,.08);
}
code{word-break:break-all;font-family:ui-monospace,SFMono-Regular,Consolas,monospace}
.ok{color:var(--ok)}
.hint{color:var(--muted);line-height:1.65}
.project{padding:14px 0;border-bottom:1px solid rgba(255,255,255,.07)}
.project:last-child{border-bottom:0;padding-bottom:0}
.list-card{padding:16px;margin-bottom:12px}
.list-card:last-child{margin-bottom:0}
.list-card strong{display:block;margin-bottom:7px;font-size:1rem}
.field{display:grid;gap:8px;margin-top:14px}
.field label{font-size:.88rem;color:#d7def2}
.input-row{display:flex;gap:10px}
.input-row input{flex:1;min-width:0}
.checkbox-row{
display:flex;
gap:12px;
align-items:flex-start;
padding:14px 16px;
}
.checkbox-row input{width:auto;margin-top:3px}
.helper{
margin-top:16px;
padding:16px;
background:linear-gradient(180deg, rgba(141,91,255,.11), rgba(55,227,218,.06));
border:1px solid rgba(141,91,255,.18);
}
dialog{
width:min(860px,94vw);
max-height:84vh;
padding:0;
border:1px solid rgba(255,255,255,.12);
border-radius:28px;
background:linear-gradient(180deg, #10172b, #09101f);
color:var(--text);
box-shadow:var(--shadow);
}
dialog::backdrop{background:rgba(0,4,12,.72);backdrop-filter:blur(10px)}
.dialog-head,.dialog-foot{padding:18px 20px}
.dialog-head{border-bottom:1px solid rgba(255,255,255,.08)}
.dialog-foot{display:flex;justify-content:flex-end;gap:10px;border-top:1px solid rgba(255,255,255,.08)}
.browser-tools{display:flex;gap:10px;padding:16px 20px 12px}
.current-path{display:block;margin:0 20px;padding:14px 16px;min-height:24px}
.folder-list{padding:16px 20px 18px;overflow:auto;max-height:48vh}
.folder{
display:flex;
width:100%;
text-align:left;
margin:7px 0;
background:linear-gradient(180deg, rgba(255,255,255,.05), rgba(255,255,255,.025));
border-radius:16px;
border:1px solid rgba(255,255,255,.07);
}
.folder:hover{border-color:rgba(117,241,231,.26)}
.footnote{margin-top:16px;font-size:.92rem;color:var(--muted)}
@media (max-width:980px){.hero-grid,.layout{grid-template-columns:1fr}}
@media (max-width:720px){
.shell{width:min(100vw - 20px,1180px);margin:16px auto 24px}
.hero,.card{padding:18px}
.grid-two{grid-template-columns:1fr}
.input-row,.actions,.toolbar,.browser-tools{flex-direction:column}
button{width:100%}
.dialog-foot{flex-direction:column-reverse}
}
</style></head><body><main class="shell">
<section class="hero">
<div class="hero-grid">
<div>
<div class="eyebrow"><span class="dot"></span> Bridge local seguro</div>
<h1>Interestellar Remote Bridge</h1>
<p class="sub">Painel local para parear o celular, autorizar pastas, registrar projetos e manter o CLI do seu ambiente pronto para receber instrucoes remotas com uma experiencia mais limpa e profissional.</p>
<div class="hero-tags">
<span class="tag">QR de pareamento</span>
<span class="tag">Acesso protegido por raizes</span>
<span class="tag">Projetos autorizados</span>
<span class="tag">Runtime local autenticado</span>
</div>
</div>
<aside class="hero-card">
<div class="status-pill"><span class="dot"></span> Painel da ponte</div>
<h2>Fluxo recomendado</h2>
<p>1. Verifique o runtime do agente. 2. Gere o QR de pareamento. 3. Autorize as raizes locais. 4. Registre seus projetos e perfis de build. O aplicativo Android usa exatamente esse painel como fonte de verdade.</p>
</aside>
</div>
</section>

<section class="layout">
<div class="stack">
<section class="card">
<div class="section-head">
<div>
<h2>Pareamento do dispositivo</h2>
<p>Gere um QR para conectar o celular ao computador atual. A rotacao de chave deve ser usada apenas quando quiser invalidar o pareamento anterior.</p>
</div>
</div>
<div class="grid-two">
<div class="metric">
<span class="label">Estado da ponte</span>
<strong id="status">Consultando…</strong>
</div>
<div class="metric">
<span class="label">Runtime local</span>
<strong id="runtimeStatus">Consultando…</strong>
</div>
</div>
<div class="qr-shell">
<div class="qr-frame"><img id="qr" width="260" alt="QR Code de pareamento"></div>
<div class="actions">
<button class="primary" onclick="pair()">Gerar QR Code</button>
<button class="subtle" onclick="pair(true)">Rotacionar chave e gerar novo QR</button>
</div>
</div>
<p class="footnote">Se voce rotacionar a chave, os dispositivos anteriores deixam de confiar nesta maquina ate um novo pareamento.</p>
</section>

<section class="card">
<div class="section-head">
<div>
<h2>Portfólio de projetos</h2>
<p>Cadastre apenas repositorios que devem ficar visiveis no celular. O build padrao e opcional e pode ser ajustado depois.</p>
</div>
</div>
<div id="projects"></div>
<div class="helper">
<strong>Novo projeto autorizado</strong>
<p class="hint">Escolha a pasta principal do workspace e, se quiser, ja deixe um comando de build padrao preparado para o app.</p>
</div>
<div class="field"><label for="projectName">Nome do projeto</label><input id="projectName" placeholder="Ex.: App Android principal"></div>
<div class="field"><label for="projectRoot">Pasta do projeto</label><div class="input-row"><input id="projectRoot" placeholder="Selecione ou informe a pasta"><button type="button" onclick="openFolderBrowser('project')">Navegar pastas</button></div></div>
<div class="field"><label for="buildExecutable">Executavel de build</label><input id="buildExecutable" placeholder="Opcional. Ex.: gradlew.bat"></div>
<div class="field"><label for="buildArguments">Argumentos do build</label><input id="buildArguments" placeholder="Opcional. Ex.: assembleDebug"></div>
<div class="actions" style="margin-top:16px"><button class="primary" onclick="addProject()">Adicionar ao portfólio</button></div>
</section>
</div>

<div class="stack">
<section class="card">
<div class="section-head">
<div>
<h2>Acesso ao sistema de arquivos</h2>
<p>Defina exatamente o que o aplicativo Android pode explorar. No modo protegido, a navegacao fica limitada as raizes abaixo e aos projetos autorizados.</p>
</div>
</div>
<label class="checkbox-row"><input id="fullFilesystem" type="checkbox" onchange="toggleFullFilesystem(this.checked)"><span><strong>Permitir acesso total ao sistema de arquivos</strong><br><span class="hint">Use apenas quando precisar de navegacao irrestrita no computador atual.</span></span></label>
<div id="authorizedRoots" style="margin-top:16px"></div>
<div class="actions" style="margin-top:16px"><button onclick="openFolderBrowser('root')">Adicionar raiz autorizada</button></div>
</section>

<section class="card">
<div class="section-head">
<div>
<h2>Boas práticas</h2>
<p>Um setup limpo evita acessos desnecessarios e deixa a experiencia remota muito mais previsivel.</p>
</div>
</div>
<div class="list-card"><strong>Use raizes enxutas</strong><span class="hint">Prefira autorizar apenas a pasta onde seus projetos realmente vivem, e nao o disco inteiro.</span></div>
<div class="list-card"><strong>Deixe o CLI autenticado</strong><span class="hint">Se o runtime do agente estiver offline ou sem login, o app nao conseguira executar instrucoes nem builds.</span></div>
<div class="list-card"><strong>Cadastre builds uteis</strong><span class="hint">Perfis simples como `assembleDebug` ou `flutter build apk` aceleram bastante o uso no celular.</span></div>
</section>

<section class="card">
<div class="section-head">
<div>
<h2>Status da conexao</h2>
<p>Resumo rapido do elo entre o portal local, o CLI do computador e o backend do Firebase.</p>
</div>
</div>
<div class="grid-two">
<div class="metric">
<span class="label">Dispositivo</span>
<strong id="deviceId" class="value-id">Consultando…</strong>
</div>
<div class="metric">
<span class="label">Firebase</span>
<strong id="firebaseStatus" class="value-success">Consultando…</strong>
</div>
</div>
<div class="list-card" style="margin-top:14px">
<strong>Diagnostico atual</strong>
<span class="hint" id="connectionSummary">Verificando conectividade da ponte…</span>
</div>
</section>
</div>
</section>

<dialog id="folderDialog"><div class="dialog-head"><h2>Selecionar pasta</h2><p class="hint">Somente pastas locais acessiveis pelo usuario atual sao exibidas aqui.</p></div>
<div class="browser-tools"><button id="folderParent" onclick="browseParent()">Voltar</button><button onclick="browseFolder('')">Unidades e locais</button></div>
<code id="folderCurrent" class="current-path">Escolha uma unidade</code><div id="folderEntries" class="folder-list"></div>
<div class="dialog-foot"><button onclick="closeFolderBrowser()">Cancelar</button><button id="folderSelect" class="primary" onclick="selectCurrentFolder()" disabled>Selecionar esta pasta</button></div></dialog>
<script>
const el=id=>document.getElementById(id);let folderState={current:null,parent:null};let folderMode='project';
async function load(){const s=await(await fetch('/api/status')).json();el('status').innerHTML=s.paired?'<span class="ok">Pareado e pronto para receber o celular</span>':'Aguardando pareamento inicial';
el('runtimeStatus').innerHTML=s.cliAvailable?'<span class="ok">CLI autenticado pela conta local</span>':'CLI compativel nao encontrado.';
el('deviceId').textContent=s.deviceId||'Nao disponivel';
el('firebaseStatus').innerHTML=s.firebaseConnected?'<span class="ok">Firebase acessivel e token valido</span>':(s.paired?'Conexao pendente: '+(s.firebaseError||'Nao foi possivel validar agora'):'Aguardando pareamento');
el('connectionSummary').textContent=s.connectionSummary||'Sem diagnostico disponível.';
const ps=await(await fetch('/api/projects')).json();const container=el('projects');container.replaceChildren();
if(!ps.length){const empty=document.createElement('div');empty.className='list-card';empty.innerHTML='<strong>Nenhum projeto autorizado</strong><span class="hint">Adicione um workspace para ele aparecer no aplicativo.</span>';container.append(empty)}
for(const p of ps){const card=document.createElement('div');card.className='list-card';const name=document.createElement('strong');name.textContent=p.name;const path=document.createElement('code');path.textContent=p.root;card.append(name,path);container.append(card)}await loadPolicy()}
async function loadPolicy(){const policy=await(await fetch('/api/filesystem/policy')).json();el('fullFilesystem').checked=policy.allowFullFilesystem;const roots=el('authorizedRoots');roots.replaceChildren();
if(!policy.roots.length){const empty=document.createElement('div');empty.className='list-card';empty.innerHTML='<strong>Nenhuma raiz adicional autorizada</strong><span class="hint">O celular vai navegar apenas pelos projetos ja cadastrados.</span>';roots.append(empty)}
for(const value of policy.roots){const row=document.createElement('div');row.className='list-card';const path=document.createElement('code');path.textContent=value;const actions=document.createElement('div');actions.className='actions';actions.style.marginTop='12px';const remove=document.createElement('button');remove.textContent='Remover raiz';remove.onclick=()=>removeRoot(value);actions.append(remove);row.append(path,actions);roots.append(row)}}
async function toggleFullFilesystem(enabled){if(enabled&&!confirm('Isso permitirá que o celular navegue por todas as unidades e pastas acessíveis ao seu usuário do Windows. Continuar?')){el('fullFilesystem').checked=false;return}const r=await fetch('/api/filesystem/policy',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({allowFullFilesystem:enabled})});if(!r.ok){alert(await r.text());await loadPolicy()}}
async function removeRoot(path){if(!confirm('Remover esta raiz autorizada? Projetos já cadastrados continuarão disponíveis.'))return;const r=await fetch('/api/filesystem/roots?path='+encodeURIComponent(path),{method:'DELETE'});if(!r.ok){alert(await r.text());return}await loadPolicy()}
async function pair(rotate=false){if(rotate&&!confirm('Rotacionar a chave? Faça isso sem tarefas em andamento.'))return;const r=await fetch('/api/pair/start?rotate='+rotate,{method:'POST'});if(!r.ok){alert(await r.text());return}const p=await r.json();el('qr').src='/api/pair/qr?token='+encodeURIComponent(p.qrToken)}
async function openFolderBrowser(mode){folderMode=mode;el('folderDialog').showModal();await browseFolder(mode==='project'?el('projectRoot').value.trim():'')}
function closeFolderBrowser(){el('folderDialog').close()}
async function browseParent(){if(folderState.parent!==null)await browseFolder(folderState.parent)}
async function browseFolder(path){const params=new URLSearchParams();if(path)params.set('path',path);params.set('scope',folderMode==='root'?'admin':'projects');const r=await fetch('/api/filesystem/directories?'+params);if(!r.ok){const e=await r.json().catch(()=>({detail:'Não foi possível abrir a pasta.'}));alert(e.detail);if(path)await browseFolder('');return}
const data=await r.json();folderState=data;el('folderCurrent').textContent=data.current||'Unidades e locais';el('folderParent').disabled=data.parent===null;el('folderSelect').disabled=!data.current;
const list=el('folderEntries');list.replaceChildren();if(!data.entries.length){const empty=document.createElement('p');empty.className='hint';empty.textContent='Nenhuma subpasta acessível.';list.append(empty)}
for(const entry of data.entries){const button=document.createElement('button');button.className='folder';button.textContent=(entry.kind==='drive'?'💽 ':'📁 ')+entry.name;button.onclick=()=>browseFolder(entry.path);list.append(button)}}
async function selectCurrentFolder(){if(!folderState.current)return;if(folderMode==='root'){const r=await fetch('/api/filesystem/roots',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({path:folderState.current})});if(!r.ok){alert(await r.text());return}closeFolderBrowser();await loadPolicy();return}el('projectRoot').value=folderState.current;if(!el('projectName').value.trim()){const parts=folderState.current.replace(/[\\\\/]+$/,'').split(/[\\\\/]/);el('projectName').value=parts.pop()||folderState.current}closeFolderBrowser()}
async function addProject(){const name=el('projectName').value.trim();const root=el('projectRoot').value.trim();const executable=el('buildExecutable').value.trim();const argumentText=el('buildArguments').value.trim();
if(!name){alert('Informe o nome do projeto.');return}if(!root){alert('Selecione a pasta do projeto.');return}
const profiles=executable?[{id:'default',name:'Build padrão',workingDirectory:'.',executable,arguments:argumentText?argumentText.split(/ +/):[],timeoutSeconds:900,artifactGlobs:[]}]:[];
const r=await fetch('/api/projects',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({id:crypto.randomUUID(),name,root,buildProfiles:profiles})});
if(!r.ok){const error=await r.json().catch(()=>({detail:'Não foi possível adicionar o projeto.'}));alert(typeof error.detail==='string'?error.detail:JSON.stringify(error.detail));return}
for(const id of ['projectName','projectRoot','buildExecutable','buildArguments'])el(id).value='';await load()}
load();setInterval(load,5000)</script></main></body></html>"""




@dataclass
class DashboardState:
    settings: Settings
    database: Database
    secrets: SecretStore
    transport: FirebaseTransport
    pairing_qr: str | None = None
    pairing_task: asyncio.Task | None = None
    on_key_ready: Any = None

    @property
    def paired(self) -> bool:
        return self.secrets.get("firebase_refresh_token") is not None


def create_dashboard(state: DashboardState) -> FastAPI:
    app = FastAPI(title="Interestellar Remote Local Dashboard", docs_url=None, redoc_url=None)

    def effective_roots() -> list[Any]:
        return state.database.list_filesystem_roots() + [
            project.root for project in state.database.list_projects()
        ]

    @app.get("/", response_class=HTMLResponse)
    async def home() -> str:
        return PAGE

    @app.get("/api/status")
    async def status() -> dict[str, Any]:
        try:
            cli_path = str(find_agy_cli())
        except FileNotFoundError:
            cli_path = ""
        firebase_connected = False
        firebase_error = ""
        if state.paired:
            try:
                await state.transport._ensure_token()
                firebase_connected = True
            except Exception as exc:
                firebase_error = str(exc)
        connection_summary = (
            "Bridge pareado, CLI pronto e Firebase respondendo."
            if state.paired and bool(cli_path) and firebase_connected
            else "Aguardando pareamento com o celular."
            if not state.paired
            else "O CLI compativel precisa estar instalado e autenticado neste computador."
            if not cli_path
            else "O bridge esta pareado, mas nao conseguiu validar a conexao com o Firebase agora."
        )
        return {
            "paired": state.paired,
            "deviceId": state.settings.device_id,
            "runtime": "antigravity-cli",
            "cliAvailable": bool(cli_path),
            "firebaseConnected": firebase_connected,
            "firebaseError": firebase_error,
            "connectionSummary": connection_summary,
        }

    @app.get("/api/projects")
    async def projects() -> list[dict[str, Any]]:
        return [p.model_dump(by_alias=True, mode="json") for p in state.database.list_projects()]

    @app.get("/api/filesystem/directories")
    async def filesystem_directories(
        path: str | None = None, scope: str = "projects"
    ) -> dict[str, Any]:
        try:
            return browse_directories(
                path,
                allowed_roots=effective_roots(),
                allow_full_filesystem=state.database.allow_full_filesystem(),
                unrestricted_local_admin=scope == "admin",
            )
        except ValueError as exc:
            raise HTTPException(400, str(exc)) from exc

    @app.post("/api/filesystem/mkdir")
    async def create_filesystem_directory(raw: dict[str, Any]) -> dict[str, Any]:
        try:
            return create_directory(
                str(raw.get("parent") or raw.get("path") or ""),
                str(raw.get("name") or ""),
                allowed_roots=effective_roots(),
                allow_full_filesystem=state.database.allow_full_filesystem(),
                unrestricted_local_admin=raw.get("scope") == "admin",
                navigate=bool(raw.get("navigate", False)),
            )
        except (ValueError, PermissionError) as exc:
            raise HTTPException(400, str(exc)) from exc

    @app.post("/api/projects/{project_id}/mkdir")
    async def create_project_dir_endpoint(project_id: str, raw: dict[str, Any]) -> dict[str, Any]:
        try:
            project = state.database.get_project(project_id)
            return create_project_directory(
                project.root,
                str(raw.get("path") or ""),
                str(raw.get("name") or ""),
            )
        except (ValueError, PermissionError) as exc:
            raise HTTPException(400, str(exc)) from exc

    @app.get("/api/filesystem/policy")
    async def filesystem_policy() -> dict[str, Any]:
        return {
            "allowFullFilesystem": state.database.allow_full_filesystem(),
            "roots": [str(root) for root in state.database.list_filesystem_roots()],
        }

    @app.post("/api/filesystem/policy")
    async def set_filesystem_policy(raw: dict[str, Any]) -> dict[str, str]:
        state.database.set_allow_full_filesystem(raw.get("allowFullFilesystem") is True)
        return {"status": "ok"}

    @app.post("/api/filesystem/roots")
    async def add_filesystem_root(raw: dict[str, Any]) -> dict[str, str]:
        try:
            root = resolve_local_directory(str(raw.get("path", "")))
            state.database.add_filesystem_root(root)
            return {"status": "ok", "path": str(root)}
        except ValueError as exc:
            raise HTTPException(400, str(exc)) from exc

    @app.delete("/api/filesystem/roots")
    async def remove_filesystem_root(path: str) -> dict[str, str]:
        try:
            root = resolve_local_directory(path)
            state.database.remove_filesystem_root(root)
            return {"status": "ok"}
        except ValueError as exc:
            raise HTTPException(400, str(exc)) from exc

    @app.post("/api/projects")
    async def add_project(raw: dict[str, Any]) -> dict[str, str]:
        try:
            project = Project.model_validate(raw)
            resolved = project.root.resolve(strict=True)
            if project.name.strip() == str(project.root).strip():
                project.name = resolved.name or str(resolved)
            if any(
                item.id != project.id and item.root.resolve() == resolved
                for item in state.database.list_projects()
            ):
                raise ValueError("Esta pasta já está no portfólio de projetos.")
            state.database.upsert_project(project)
            return {"status": "ok"}
        except Exception as exc:
            raise HTTPException(400, str(exc)) from exc

    @app.post("/api/pair/start")
    async def start_pairing(rotate: bool = False) -> dict[str, str]:
        if state.pairing_task and not state.pairing_task.done():
            if not rotate and state.pairing_qr:
                return {"qrToken": state.pairing_qr}
            state.pairing_task.cancel()
            await asyncio.gather(state.pairing_task, return_exceptions=True)
            state.pairing_task = None
        root_key = os.urandom(32) if rotate else (state.secrets.get("root_key") or os.urandom(32))
        current_version = int((state.secrets.get("key_version") or b"0").decode())
        key_version = current_version + 1 if rotate or current_version == 0 else current_version
        secret = b64e(os.urandom(32))
        verifier = hashlib.sha256(secret.encode()).hexdigest()
        result = await state.transport.start_pairing(verifier, "encrypted-on-sync")
        pairing_id = result["pairingId"]
        qr_data = {
            "version": 1,
            "deviceId": state.settings.device_id,
            "deviceName": state.settings.device_name,
            "pairingId": pairing_id,
            "secret": secret,
            "rootKey": b64e(root_key),
            "keyVersion": key_version,
            "expiresAt": result["expiresAt"],
        }
        state.pairing_qr = b64e(json.dumps(qr_data, separators=(",", ":")).encode())

        async def wait_for_claim() -> None:
            deadline = time.monotonic() + 600
            while time.monotonic() < deadline:
                if await state.transport.complete_pairing(pairing_id, secret):
                    state.secrets.put("root_key", root_key)
                    state.secrets.put("key_version", str(key_version).encode())
                    if state.transport.refresh_token:
                        state.secrets.put(
                            "firebase_refresh_token", state.transport.refresh_token.encode()
                        )
                    if state.on_key_ready:
                        state.on_key_ready(root_key, key_version)
                    return
                await asyncio.sleep(3)

        state.pairing_task = asyncio.create_task(wait_for_claim())
        return {"qrToken": state.pairing_qr}

    @app.get("/api/pair/qr")
    async def pairing_qr(token: str) -> Response:
        if token != state.pairing_qr:
            raise HTTPException(404)
        image = qrcode.make(f"agyremote://pair?payload={token}")
        buffer = io.BytesIO()
        image.save(buffer, format="PNG")
        return Response(buffer.getvalue(), media_type="image/png")

    return app
