# sports-agent

A Claude tool-use agent on top of the [sports-simulators](https://github.com/jamarius997-a11y/sports-simulators) engine (NBA, NFL, NHL, college football, college basketball).

Claude runs the loop; the simulator, picks log, track record and news are its tools:

| Tool | What it does |
|---|---|
| `list_games` | Upcoming games with line, total, moneylines |
| `simulate_matchup` | Monte Carlo sim for one game (uses rest, weather, confirmed goalies when scheduled) |
| `get_picks` | Logged sim-vs-market win probabilities, sorted by gap |
| `get_track_record` | Live scored record vs market and backtest |
| `get_news` | Injury/availability headlines |
| `list_teams` | Resolve team names |
| `get_weekly_report` | Latest drift/health report |

## Run

```bash
npm install
export ANTHROPIC_API_KEY=...     # or `ant auth login`
node agent.js                    # interactive
node agent.js "Who wins Lakers at Suns, and should I trust the sim?"
npm run test:tools               # offline smoke test, no API key needed
```

`SPORTS_AGENT_MODEL` overrides the model (default `claude-opus-5-5`).

## Data

`engine/` is a snapshot of the simulator and its data files. The upstream repo refreshes them on a schedule; to update the snapshot:

```bash
git clone --depth 1 https://github.com/jamarius997-a11y/sports-simulators ../../jamarius997-a11y/sports-simulators
./sync-engine.sh [path-to-checkout]
```

The agent reports data age via `get_track_record`. Analysis only, not betting advice: the live record shows the sim usually trails the market, and the agent is prompted to say so.
