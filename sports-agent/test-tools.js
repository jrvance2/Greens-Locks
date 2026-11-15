'use strict';
// Offline smoke test: runs every tool against the vendored engine (no API key needed).
const tools = require('./tools'), E = require('./engine');
(async () => {
  const by = Object.fromEntries(tools.map(t => [t.name, t]));
  const g = E.listGames('nba', null, 1)[0];
  console.log('first nba game:', g);
  const cases = [['list_games', { sport: 'nba', limit: 2 }], ['simulate_matchup', { sport: 'nba', away: g.away, home: g.home, sims: 500 }], ['get_picks', { min_gap: 0.05 }], ['get_track_record', { sport: 'nba' }], ['get_news', { sport: 'nba' }], ['list_teams', { sport: 'nhl' }], ['get_weekly_report', {}]];
  for (const [n, input] of cases) { const out = await by[n].run(input); console.log('\n' + n + ' ->', String(out).slice(0, 300)); if (!out) throw new Error(n + ' returned nothing'); }
  console.log('\nall tools ok');
})().catch(e => { console.error(e); process.exit(1); });
