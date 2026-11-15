'use strict';
const { betaZodTool } = require('@anthropic-ai/sdk/helpers/beta/zod');
const { z } = require('zod');
const E = require('./engine');
const sport = z.enum(E.SPORTS).describe('nba, nfl, nhl, ncaaf (college football) or ncaab (college basketball)');
const J = o => JSON.stringify(o);

module.exports = [
  betaZodTool({
    name: 'refresh_data', description: 'Re-pull team ratings, schedules, injuries and news, re-run the simulator over upcoming games, and score finished games. Takes a few minutes and needs internet access. Only replaces the existing data if the refresh succeeds and passes the health check. Use when get_track_record shows the data is more than ~6 hours old and the user wants fresh numbers.',
    inputSchema: z.object({}),
    run: async () => J(await E.refresh()),
  }),
  betaZodTool({
    name: 'list_games', description: 'Upcoming scheduled games for a sport with the posted line, total and moneylines. Optionally filter by team name or abbreviation.',
    inputSchema: z.object({ sport, team: z.string().optional(), limit: z.number().int().min(1).max(50).optional() }),
    run: async ({ sport, team, limit }) => J(E.listGames(sport, team, limit)),
  }),
  betaZodTool({
    name: 'simulate_matchup', description: 'Run the Monte Carlo game simulator for one matchup (team names or abbreviations). Returns win probabilities, average score/margin/total and key injuries. If the game is on the schedule it uses rest, weather, confirmed goalies and returns the market line.',
    inputSchema: z.object({ sport, away: z.string(), home: z.string(), sims: z.number().int().min(200).max(10000).optional().describe('default 2000'), neutral: z.boolean().optional().describe('neutral-site game') }),
    run: async ({ sport, away, home, sims, neutral }) => J(E.simulate(sport, away, home, sims, neutral)),
  }),
  betaZodTool({
    name: 'get_picks', description: "Today's logged pre-game sim vs market comparison for upcoming games, sorted by how far the sim differs from the market's win probability.",
    inputSchema: z.object({ sport: sport.optional(), min_gap: z.number().min(0).max(1).optional().describe('only games where |sim - market| win probability is at least this, e.g. 0.05') }),
    run: async ({ sport, min_gap }) => J(E.picks(sport, min_gap)),
  }),
  betaZodTool({
    name: 'get_track_record', description: 'Live scored record of the sim vs the market and the backtest accuracy, per sport (or all sports). Use it before telling the user how much to trust the sim.',
    inputSchema: z.object({ sport: sport.optional() }),
    run: async ({ sport }) => J({ dataAgeHours: E.dataAgeHours(), record: E.record(sport) }),
  }),
  betaZodTool({
    name: 'get_news', description: 'Recent injury/availability headlines the pipeline flagged, optionally filtered by sport and team or player.',
    inputSchema: z.object({ sport: sport.optional(), query: z.string().optional().describe('team or player name') }),
    run: async ({ sport, query }) => J(E.news(sport, query)),
  }),
  betaZodTool({
    name: 'list_teams', description: 'List team abbreviations and names for a sport, to resolve a name before simulating.',
    inputSchema: z.object({ sport }),
    run: async ({ sport }) => E.listTeams(sport),
  }),
  betaZodTool({
    name: 'get_weekly_report', description: 'The most recent weekly health and accuracy report (drift flags, blend weights, feed checks).',
    inputSchema: z.object({}),
    run: async () => { const r = E.weeklyReport(); return r ? r.text : 'No weekly report available.'; },
  }),
];
