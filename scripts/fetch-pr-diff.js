#!/usr/bin/env node
// fetch-pr-diff.js — Fetches PR diff from GitHub API using Octokit
//
// Uso:
//   node scripts/fetch-pr-diff.js <PR_NUMBER>
//
// Auth (en orden de prioridad):
//   1. gh CLI  — si está instalado y autenticado (`gh auth login`)
//   2. GITHUB_TOKEN en scripts/.env
//
// Output: ai-output/diff.txt + ai-output/diff-meta.txt (mismo formato que generate-diff.sh)

import { Octokit } from '@octokit/rest';
import { readFileSync, writeFileSync, mkdirSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';
import { execSync, spawnSync } from 'child_process';

const __dirname = dirname(fileURLToPath(import.meta.url));

// Load scripts/.env
try {
  const envContent = readFileSync(resolve(__dirname, '.env'), 'utf8');
  for (const line of envContent.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eqIdx = trimmed.indexOf('=');
    if (eqIdx === -1) continue;
    const key = trimmed.slice(0, eqIdx).trim();
    const val = trimmed.slice(eqIdx + 1).trim();
    if (key && !(key in process.env)) process.env[key] = val;
  }
} catch { /* .env opcional */ }

const PR_NUMBER = parseInt(process.argv[2]);
if (!PR_NUMBER || isNaN(PR_NUMBER)) {
  console.error('Uso: node scripts/fetch-pr-diff.js <PR_NUMBER>');
  process.exit(1);
}

function detectRepo() {
  if (process.env.GITHUB_REPO) return process.env.GITHUB_REPO;
  try {
    const url = execSync('git remote get-url origin', { encoding: 'utf8' }).trim();
    const match = url.match(/github\.com[:/]([^/]+\/[^/]+?)(?:\.git)?$/);
    if (match) return match[1];
  } catch { /* ignore */ }
  throw new Error('No se pudo detectar el repo. Define GITHUB_REPO=owner/repo en scripts/.env');
}

// Intenta obtener el token de gh CLI primero, luego de .env
function resolveToken() {
  const ghResult = spawnSync('gh', ['auth', 'token'], { encoding: 'utf8' });
  if (ghResult.status === 0 && ghResult.stdout.trim()) {
    console.log('Auth: usando gh CLI');
    return ghResult.stdout.trim();
  }
  const envToken = process.env.GITHUB_TOKEN;
  if (envToken) {
    console.log('Auth: usando GITHUB_TOKEN de .env');
    return envToken;
  }
  console.error([
    'Error: no se encontró autenticación de GitHub.',
    '',
    'Opción A (recomendada) — GitHub CLI:',
    '  winget install GitHub.cli',
    '  gh auth login',
    '',
    'Opción B — Token manual:',
    '  Agrega GITHUB_TOKEN=ghp_... en scripts/.env',
    '  (GitHub → Settings → Developer settings → Personal access tokens)',
  ].join('\n'));
  process.exit(1);
}

const RELEVANT_EXT = /\.(kt|java|gradle|gradle\.kts|xml|json|md)$/;

async function main() {
  const token = resolveToken();

  const repoSlug = detectRepo();
  const [owner, repo] = repoSlug.split('/');
  const octokit = new Octokit({ auth: token });

  console.log(`Fetching PR #${PR_NUMBER} de ${repoSlug}...`);

  // Las 3 llamadas en paralelo: metadata, archivos y commits
  const [prRes, files, commits] = await Promise.all([
    octokit.pulls.get({ owner, repo, pull_number: PR_NUMBER }),
    octokit.paginate(octokit.pulls.listFiles, { owner, repo, pull_number: PR_NUMBER, per_page: 100 }),
    octokit.paginate(octokit.pulls.listCommits, { owner, repo, pull_number: PR_NUMBER, per_page: 100 }),
  ]);

  const pr = prRes.data;

  // diff.txt — formato unified diff compatible con git diff
  const relevantFiles = files.filter(f => RELEVANT_EXT.test(f.filename) && f.patch);
  const diffContent = relevantFiles.map(f => [
    `diff --git a/${f.filename} b/${f.filename}`,
    `index 0000000..0000000 100644`,
    `--- a/${f.filename}`,
    `+++ b/${f.filename}`,
    f.patch,
  ].join('\n')).join('\n\n');

  // diff-meta.txt — mismo formato que generate-diff.sh
  const nameStatus = files.map(f => {
    const s = { added: 'A', removed: 'D', renamed: 'R' }[f.status] ?? 'M';
    return `${s}\t${f.filename}`;
  }).join('\n');

  const commitLog = commits
    .map(c => `${c.sha.slice(0, 7)} ${c.commit.message.split('\n')[0]}`)
    .join('\n');

  const metaContent = `fecha: ${new Date().toISOString()}
base_branch: ${pr.base.ref}
compare_branch: ${pr.head.ref}
pr_number: ${PR_NUMBER}
pr_title: ${pr.title}
pr_author: ${pr.user.login}
repo: ${repoSlug}
current_commit: ${pr.head.sha}
base_commit: ${pr.base.sha}

--- Archivos cambiados ---
${nameStatus}

--- Commits en la rama ---
${commitLog}`;

  // Escribir output
  const outputDir = resolve(__dirname, '..', 'ai-output');
  mkdirSync(outputDir, { recursive: true });
  writeFileSync(resolve(outputDir, 'diff.txt'), diffContent, 'utf8');
  writeFileSync(resolve(outputDir, 'diff-meta.txt'), metaContent, 'utf8');

  const diffLines = diffContent.split('\n').length;
  console.log(`✓ PR #${PR_NUMBER}: "${pr.title}"`);
  console.log(`✓ ${files.length} archivos cambiados (${relevantFiles.length} con diff relevante)`);
  console.log(`✓ ${commits.length} commits`);
  console.log(`✓ ai-output/diff.txt (${diffLines} líneas)`);
  console.log(`✓ ai-output/diff-meta.txt`);
  console.log('');
  console.log('Siguiente paso: ejecutar /diff-analyzer en Claude Code');
}

main().catch(err => {
  console.error('Error:', err.message);
  process.exit(1);
});
