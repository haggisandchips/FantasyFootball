# Fantasy Football

A Spring Boot CLI that fetches live Fantasy Premier League data and:

- builds a "Killer Team" - the highest scoring, affordable, valid 15-man squad buildable from
  scratch (see `calculation.KillerTeamFinder`)
- suggests point-improving transfers for your own squad, within your free transfers and budget
  (see `calculation.TransferSelector`)

Run with `./gradlew bootRun` - this opens a desktop window (JavaFX) rather than just logging to the
console, though the same analysis is still logged for debugging.

## Desktop UI

The window has three tabs. My Squad and Transfers load automatically on startup (in that order,
each in the background so the window stays responsive); Killer Team only calculates when you ask
it to, since it's a much heavier search.

- **My Squad** - your squad in "Pick Team" view: starting XI grouped by position, a separator, then
  your substitutes below, plus squad value, free transfers and season-total ("overall") points.
  In stub mode, overall points comes from the `overallPoints` field in `my-squad.json` (shown as
  "N/A" if that field is left out) since there's no real FPL entry to fetch it from.
- **Transfers** - your top 5 suggested transfers, calculated once My Squad has loaded. If you have
  2 free transfers, suggestions are calculated for both 1 and 2 transfers; 2 is shown by default,
  with a toggle to switch to 1. Below that, any of your players who are currently injured or
  doubtful are listed with their status, chance of playing and news.
- **Killer Team** - the highest scoring, affordable, valid 15-man squad buildable from scratch (see
  `calculation.KillerTeamFinder`), independent of your own squad. Not calculated automatically -
  click "Calculate" to run it in the background.

## Fetching your own squad

Transfer suggestions need your actual squad, money available and free transfers. There are two
ways to provide that, controlled by the `FPL_AUTH_ENABLED` environment variable.

### Stub mode (default)

If `FPL_AUTH_ENABLED` isn't set to `true`, `FileSquadProvider` reads a `my-squad.json` file from
the project root. Copy `my-squad.example.json` to `my-squad.json` and edit it:

```json
{
  "freeTransfers": 1,
  "moneyAvailable": 2.5,
  "overallPoints": 82,
  "startingEleven": ["Salah", "Haaland", "..."],
  "substitutes": ["..."]
}
```

Names in both lists are matched by FPL's short display name (case-insensitive) against live player
data. `startingEleven`/`substitutes` drive the Pick Team view but aren't validated as a legal
formation - list whoever you like in each. `overallPoints` is optional - a season-total points
figure you type in yourself (there's no stub equivalent of fetching it live); leave it out and the
UI shows "N/A". `my-squad.json` is gitignored, so your picks never get committed. Selling price is
approximated as the current buy price (your real selling price depends on purchase history, which
this mode has no way to know), and there's no stub equivalent of a captain/vice-captain since that
isn't part of this file format.

If you have an older `my-squad.json` with a flat `players` list, split it into `startingEleven`
(first 11) and `substitutes` (last 4) - the split doesn't need to be a real formation.

### Real fetching

Set `FPL_AUTH_ENABLED=true` and `FPL_API_AUTHORIZATION=<token>` to use `AuthenticatedSquadProvider`
instead, which fetches your actual picks, bank and free transfers from the FPL API.

Premier League's login is a JS-driven identity provider (Ping Identity) with no public API or
OAuth client registration, so this app can't perform the login itself. Instead, capture the bearer
token your own browser already uses:

1. Log in to <https://fantasy.premierleague.com> normally in your browser.
2. Open dev tools -> Network tab, then visit a page that needs your identity (e.g. "Points" or "My
   Team") so it calls the API.
3. Find a request to `fantasy.premierleague.com/api/me/` or `/api/my-team/...`.
4. Copy its `X-Api-Authorization` request header value (verbatim, whatever prefix/format it has).

```bash
FPL_AUTH_ENABLED=true FPL_API_AUTHORIZATION='...' ./gradlew bootRun
```

No session cookie is needed alongside the token - that was tried and confirmed unnecessary.

The token expires periodically; if a run fails with an authentication error, capture a fresh one.
This is inherently a bit fragile since it relies on an undocumented API and an unofficial way in -
it can break if Premier League changes how their frontend authenticates. Automating capture of the
token (via an embedded browser) is planned as part of a future desktop UI.
