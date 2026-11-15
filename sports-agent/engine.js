'use strict';
// Loads the vendored simulator engine (engine/) once and exposes plain functions the agent's tools call.
const fs = require('fs'), path = require('path');
const DIR = path.join(__dirname, 'engine');
global.window = global;
const Sim = require(path.join(DIR, 'sim.js'));
const Travel = require(path.join(DIR, 'travel.js'));
for (const f of ['data.js', 'backtest-results.js', 'picks-results.js', 'blend-weights.js', 'news-data.js']) { try { require(path.join(DIR, f)); } catch (e) { /* optional */ } }
const D = window.LIVE_DATA, B = (window.BACKTEST && window.BACKTEST.leagues) || {}, P = (window.PICKS && window.PICKS.leagues) || {}, N = window.NEWS || { items: [] };
Sim.applyLive(D);
const SPORTS = ['nba', 'nfl', 'nhl', 'ncaaf', 'ncaab'];
const norm = x => String(x || '').toLowerCase().replace(/[^a-z0-9]/g, '');
const rng = Sim.makeRng((Date.now() & 0xffffffff) >>> 0);

function findTeam(sp, q) {
  const T = Sim.SPORTS[sp].teams, n = norm(q);
  return T.find(t => norm(t.id) === n || norm(t.name) === n) || T.find(t => norm(t.name).includes(n) && n.length >= 3) || null;
}
const games = sp => (D.schedule && D.schedule[sp]) || [];
function upcoming(sp) { return games(sp).filter(g => new Date(g.date).getTime() > Date.now()); }
function findGame(sp, away, home) {
  return upcoming(sp).find(g => norm(g.away.abbr) === norm(away.id) && norm(g.home.abbr) === norm(home.id) || norm(g.away.name) === norm(away.name) && norm(g.home.name) === norm(home.name)) || null;
}
const logAll = () => JSON.parse(fs.readFileSync(path.join(DIR, 'picks-log.json'), 'utf8'));

function listGames(sp, team, limit) {
  const q = norm(team);
  return upcoming(sp).filter(g => !q || [g.away, g.home].some(s => norm(s.name).includes(q) || norm(s.abbr) === q)).slice(0, limit || 15)
    .map(g => ({ id: g.id, start: g.date, away: g.away.name, home: g.home.name, line: g.line, total: g.total, awayML: g.awayML, homeML: g.homeML }));
}

function simulate(sp, awayQ, homeQ, sims, neutral) {
  const away = findTeam(sp, awayQ), home = findTeam(sp, homeQ);
  if (!away) return { error: 'team not found: ' + awayQ + ' (try list_teams)' };
  if (!home) return { error: 'team not found: ' + homeQ + ' (try list_teams)' };
  const g = findGame(sp, away, home), opts = { neutral: !!neutral };
  if (g) {
    opts.rest = { away: g.awayRest, home: g.homeRest }; opts.weather = g.weather; opts.neutral = !!(g.venue && g.venue.neutral) || !!neutral;
    if (sp === 'nhl') [[away, g.away], [home, g.home]].forEach(([t, s]) => {
      const R = Sim.roster('nhl', t); t.gIdx = -1; if (!R.goalies) return;
      let i = Sim.matchGoalie(R.goalies, s.confirmed); if (i < 0 && s.probable) i = R.goalies.findIndex(x => x.name === s.probable);
      if (i >= 0) t.gIdx = i;
    });
  }
  const n = Math.min(Math.max(sims || 2000, 200), 10000), m = Sim.monteCarlo(sp, away, home, n, rng, opts);
  const out = {
    sport: sp, away: away.name, home: home.name, sims: n, scheduledGame: g ? { id: g.id, start: g.date } : null,
    pHomeWin: +((m.wins[1] + m.wins[2] / 2) / n).toFixed(3), pAwayWin: +((m.wins[0] + m.wins[2] / 2) / n).toFixed(3),
    avgScore: { away: +m.avg[0].toFixed(1), home: +m.avg[1].toFixed(1) }, avgMargin_homeMinusAway: +(m.avg[1] - m.avg[0]).toFixed(1), avgTotal: +m.avgTotal.toFixed(1),
    injuries: { away: Sim.injuries(sp, away), home: Sim.injuries(sp, home) },
    note: 'Raw sim probability. Backtests show the sim trails the betting market, so treat the market as the baseline and the sim as a second opinion.',
  };
  if (g) out.market = { line: g.line, total: g.total, homeML: g.homeML, awayML: g.awayML, espnPHome: g.espnP };
  return out;
}

function picks(sp, minGap) {
  const gap = minGap == null ? 0 : minGap;
  return Object.values(logAll()).filter(e => !e.result && new Date(e.start).getTime() > Date.now() && (!sp || e.league === sp) && e.mkt && e.mkt.pHome != null)
    .map(e => ({ game: e.away.name + ' @ ' + e.home.name, league: e.league, start: e.start, simPHome: e.simPHome, marketPHome: +e.mkt.pHome.toFixed(3), blendPHome: e.blendPHome, gap: +(e.simPHome - e.mkt.pHome).toFixed(3), simMargin: e.simMargin, line: e.mkt.line, simTotal: e.simTotal, total: e.mkt.total, injuries: e.inj }))
    .filter(r => Math.abs(r.gap) >= gap).sort((a, b) => Math.abs(b.gap) - Math.abs(a.gap)).slice(0, 25);
}

function record(sp) {
  const pick = s => ({ sport: s, liveScored: (P[s] || {}).n, pending: (P[s] || {}).pending, liveSimWinnerAcc: (P[s] || {}).acc, liveMarketWinnerAcc: (P[s] || {}).accMkt, simBrier: (P[s] || {}).brierSimMkt, marketBrier: (P[s] || {}).brierMkt, backtestAcc: (B[s] || {}).acc, spreadCallsRight: (P[s] || {}).hitCallS, spreadCalls: (P[s] || {}).nCallS, totalCallsRight: (P[s] || {}).hitCallT, totalCalls: (P[s] || {}).nCallT });
  return (sp ? [sp] : SPORTS).map(pick);
}

function news(sp, team) {
  const q = norm(team);
  return N.items.filter(i => (!sp || i.sport === sp) && (!q || norm(i.team).includes(q) || norm(i.player).includes(q))).slice(0, 25).map(i => ({ sport: i.sport, team: i.team, player: i.player, status: i.status, headline: i.title, source: i.source }));
}

function listTeams(sp) { return Sim.SPORTS[sp].teams.map(t => t.id + ' ' + t.name).join('; '); }

function weeklyReport() {
  const dir = path.join(DIR, 'reports'); if (!fs.existsSync(dir)) return null;
  const f = fs.readdirSync(dir).filter(x => x.endsWith('.md')).sort().pop();
  return f ? { file: f, text: fs.readFileSync(path.join(dir, f), 'utf8') } : null;
}
const dataAgeHours = () => +((Date.now() - new Date(D.generated).getTime()) / 3600e3).toFixed(1);
module.exports = { SPORTS, listGames, simulate, picks, record, news, listTeams, weeklyReport, dataAgeHours };
