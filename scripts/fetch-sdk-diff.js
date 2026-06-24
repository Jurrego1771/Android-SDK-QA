#!/usr/bin/env node
// ============================================================================
// fetch-sdk-diff.js — Obtiene el diff REAL del repo del SDK (cross-repo) vía GitHub API.
//
// A diferencia de fetch-pr-diff.js (que apunta al repo actual por su git remote), este apunta
// SIEMPRE al repo del SDK (env SDK_REPO=owner/repo) y soporta los dos disparadores del flujo:
//
//   node scripts/fetch-sdk-diff.js pr <PR_NUMBER>          # PR → diff base...head + título/descr.
//   node scripts/fetch-sdk-diff.js compare <BASE> <HEAD>   # push → diff before..after
//
// Produce el MISMO contrato que consume /diff-analyzer:
//   ai-output/diff.txt       (unified diff de los archivos relevantes)
//   ai-output/diff-meta.txt  (rama, commits, PR title/author si aplica)
//
// El diff del código es la fuente de verdad — capta cambios que un changelog escrito omite.
//
// Auth (orden): gh CLI (`gh auth token`) → GITHUB_TOKEN (env o scripts/.env). El token debe tener
// lectura de Contents/Pull requests del repo del SDK.
// ============================================================================
import { Octokit } from '@octokit/rest';
import { readFileSync, writeFileSync, mkdirSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';
import { spawnSync } from 'child_process';

const __dirname = dirname(fileURLToPath(import.meta.url));

// Cargar scripts/.env (opcional)
try {
  const envContent = readFileSync(resolve(__dirname, '.env'), 'utf8');
  for (const line of envContent.split('\n')) {
    const t = line.trim();
    if (!t || t.startsWith('#')) continue;
    const i = t.indexOf('=');
    if (i === -1) continue;
    const k = t.slice(0, i).trim();
    if (k && !(k in process.env)) process.env[k] = t.slice(i + 1).trim();
  }
} catch { /* .env opcional */ }

const MODE = process.argv[2];
const SDK_REPO = process.env.SDK_REPO;

if (!SDK_REPO || !/^[^/]+\/[^/]+$/.test(SDK_REPO)) {
  console.error('Falta SDK_REPO=owner/repo (repo del SDK). Ej: SDK_REPO=mediastream/MediastreamPlatformSDKAndroid');
  process.exit(2);
}
if (MODE !== 'pr' && MODE !== 'compare') {
  console.error('Uso: node scripts/fetch-sdk-diff.js pr <PR_NUMBER> | compare <BASE_SHA> <HEAD_SHA>');
  process.exit(2);
}

function resolveToken() {
  const gh = spawnSync('gh', ['auth', 'token'], { encoding: 'utf8' });
  if (gh.status === 0 && gh.stdout.trim()) { console.log('Auth: gh CLI'); return gh.stdout.trim(); }
  if (process.env.GITHUB_TOKEN) { console.log('Auth: GITHUB_TOKEN'); return process.env.GITHUB_TOKEN; }
  console.error('Sin auth de GitHub: define GITHUB_TOKEN (con lectura del repo del SDK) o usa gh auth login.');
  process.exit(2);
}

// Solo el código/config que puede cambiar el comportamiento bajo test. Se excluye ruido (docs/tests del SDK).
const RELEVANT_EXT = /\.(kt|java|gradle|gradle\.kts|xml|json|pro)$/;
// Ruido que no aporta señal de comportamiento: IDE, build, lockfiles.
const NOISE_PATH = /(^|\/)\.idea\/|(^|\/)build\/|\.lock$|gradle\/wrapper\//;

function buildDiff(files) {
  const relevant = files.filter(f => RELEVANT_EXT.test(f.filename) && !NOISE_PATH.test(f.filename) && f.patch);
  const diff = relevant.map(f => [
    `diff --git a/${f.filename} b/${f.filename}`,
    `--- a/${f.filename}`,
    `+++ b/${f.filename}`,
    f.patch,
  ].join('\n')).join('\n\n');
  const nameStatus = files
    .map(f => `${({ added: 'A', removed: 'D', renamed: 'R' }[f.status] ?? 'M')}\t${f.filename}`)
    .join('\n');
  return { diff, nameStatus, relevantCount: relevant.length, total: files.length };
}

function writeOut(diff, meta) {
  const outDir = resolve(__dirname, '..', 'ai-output');
  mkdirSync(outDir, { recursive: true });
  writeFileSync(resolve(outDir, 'diff.txt'), diff, 'utf8');
  writeFileSync(resolve(outDir, 'diff-meta.txt'), meta, 'utf8');
}

async function main() {
  const [owner, repo] = SDK_REPO.split('/');
  const octokit = new Octokit({ auth: resolveToken() });

  if (MODE === 'pr') {
    const prNum = parseInt(process.argv[3]);
    if (!prNum) { console.error('Falta PR_NUMBER'); process.exit(2); }
    console.log(`SDK ${SDK_REPO} — PR #${prNum}…`);
    const [prRes, files, commits] = await Promise.all([
      octokit.pulls.get({ owner, repo, pull_number: prNum }),
      octokit.paginate(octokit.pulls.listFiles, { owner, repo, pull_number: prNum, per_page: 100 }),
      octokit.paginate(octokit.pulls.listCommits, { owner, repo, pull_number: prNum, per_page: 100 }),
    ]);
    const pr = prRes.data;
    const { diff, nameStatus, relevantCount, total } = buildDiff(files);
    const commitLog = commits.map(c => `${c.sha.slice(0, 7)} ${c.commit.message.split('\n')[0]}`).join('\n');
    const meta = `fecha: ${new Date().toISOString()}
source: sdk-pr
base_branch: ${pr.base.ref}
compare_branch: ${pr.head.ref}
pr_number: ${prNum}
pr_title: ${pr.title}
pr_author: ${pr.user.login}
pr_body: ${(pr.body || '').replace(/\r?\n/g, ' ⏎ ').slice(0, 2000)}
repo: ${SDK_REPO}
base_commit: ${pr.base.sha}
current_commit: ${pr.head.sha}

--- Archivos cambiados ---
${nameStatus}

--- Commits ---
${commitLog}`;
    writeOut(diff, meta);
    console.log(`✓ "${pr.title}" — ${total} archivos (${relevantCount} relevantes), ${commits.length} commits`);
  } else {
    const base = process.argv[3], head = process.argv[4];
    if (!base || !head) { console.error('Faltan BASE_SHA HEAD_SHA'); process.exit(2); }
    console.log(`SDK ${SDK_REPO} — compare ${base.slice(0, 7)}..${head.slice(0, 7)}…`);
    const cmp = await octokit.repos.compareCommitsWithBasehead({ owner, repo, basehead: `${base}...${head}` });
    const files = cmp.data.files || [];
    const { diff, nameStatus, relevantCount, total } = buildDiff(files);
    const commitLog = (cmp.data.commits || [])
      .map(c => `${c.sha.slice(0, 7)} ${c.commit.message.split('\n')[0]}`).join('\n');
    const meta = `fecha: ${new Date().toISOString()}
source: sdk-push
repo: ${SDK_REPO}
base_commit: ${base}
current_commit: ${head}

--- Archivos cambiados ---
${nameStatus}

--- Commits ---
${commitLog}`;
    writeOut(diff, meta);
    console.log(`✓ compare — ${total} archivos (${relevantCount} relevantes)`);
  }
  console.log('✓ ai-output/diff.txt + diff-meta.txt');
}

main().catch(err => { console.error('Error:', err.message); process.exit(1); });
