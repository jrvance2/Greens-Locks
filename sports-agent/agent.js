#!/usr/bin/env node
'use strict';
// Sports-simulator agent: Claude calls the simulator, picks log, track record and news as tools.
//   node agent.js                      interactive chat
//   node agent.js "who wins LAL @ PHX?" one-shot
const readline = require('readline');
const Anthropic = require('@anthropic-ai/sdk').default || require('@anthropic-ai/sdk');
const tools = require('./tools');
const E = require('./engine');

const MODEL = process.env.SPORTS_AGENT_MODEL || 'claude-opus-5-5';
const SYSTEM = `You are a sports analytics agent built on a Monte Carlo game simulator (NBA, NFL, NHL, college football, college basketball).
Use the tools for every number: never guess probabilities, lines or injuries. Typical flow: list_games or get_picks to find games, simulate_matchup for a specific game, get_news for availability context, get_track_record before saying how much the sim should be trusted.
Be honest about calibration: the backtests and live record show the sim usually trails the betting market, so present the market as the baseline and the sim as a second opinion; a big gap more often means a stale input (injury, goalie, rest) than an edge. Say when data is old (data age is in get_track_record). This is analysis, not betting advice or a guarantee. Keep answers short and lead with the answer.`;

const client = new Anthropic();
const history = [];

async function turn(text) {
  history.push({ role: 'user', content: text });
  const final = await client.beta.messages.toolRunner({
    model: MODEL, max_tokens: 16000, thinking: { type: 'adaptive' }, output_config: { effort: 'medium' },
    betas: ['server-side-fallback-2026-07-01'], fallbacks: 'default',   // re-run on a fallback model if the request is declined
    system: SYSTEM, tools, messages: history,
  });
  history.push({ role: 'assistant', content: final.content });
  if (final.stop_reason === 'refusal') return '(the request was declined)';
  return final.content.filter(b => b.type === 'text').map(b => b.text).join('\n');
}

(async () => {
  const oneShot = process.argv.slice(2).join(' ').trim();
  if (oneShot) { console.log(await turn(oneShot)); return; }
  console.log('Sports agent (' + MODEL + '), data ' + E.dataAgeHours() + 'h old. Ctrl+C to exit.');
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout, prompt: '> ' });
  rl.prompt();
  rl.on('line', async line => {
    if (line.trim()) { try { console.log('\n' + await turn(line.trim()) + '\n'); } catch (e) { console.error('error:', e.message); } }
    rl.prompt();
  });
})().catch(e => { console.error(e.message); process.exit(1); });
