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
  In stub mode, overall points comes from the `overallPoints` field in your squad file (shown as
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
ways to provide that, controlled by the `FPL_MY_SQUAD_FILE` environment variable.

### Real fetching (default)

With `FPL_MY_SQUAD_FILE` unset, `AuthenticatedSquadProvider` fetches your actual picks, bank and
free transfers from the FPL API:

```bash
./gradlew bootRun
```

Premier League's login is a JS-driven identity provider (Ping Identity) with no public API or
OAuth client registration, so this app can't perform the login itself, and - after extensively
trying to automate it - can't safely observe it either: embedding a real browser to capture the
resulting token was tried with both JavaFX's own `WebView` (missing `window.crypto.subtle`/
`indexedDB`, and no way to see past Datadome's anti-tampering JS on FPL's site) and JCEF/Chromium in
both of its rendering modes (windowed mode renders but ignores all input; off-screen rendering needs
a working OpenGL context that isn't available on every machine) - every path hit an unresolved,
machine-specific native issue. So instead, an "Account" menu in the desktop UI has a **Log in to
FPL...** item that asks you to paste the token yourself: log in to fantasy.premierleague.com in your
own browser, open dev tools' Network tab, find a request to an authenticated endpoint (e.g.
`/api/me/`), copy the value of its `X-Api-Authorization` request header, and paste it in. If no
token is available yet, this dialog opens automatically on startup.

Digging through dev tools every time gets old fast, so there's a faster way: a bookmarklet that
finds the token in `localStorage` (FPL's frontend, built on `oidc-client-ts`, keeps it there under
an `oidc.user:...` key) and copies it straight to your clipboard. Create a new bookmark with this as
its URL (the whole thing, starting with `javascript:`):

```javascript
javascript:(function(){try{for(let i=0;i<localStorage.length;i++){const k=localStorage.key(i);if(k&&k.indexOf('oidc.user:')===0){const d=JSON.parse(localStorage.getItem(k));if(d&&d.access_token){const v=(d.token_type||'Bearer')+' '+d.access_token;navigator.clipboard.writeText(v).then(()=>alert('FPL token copied to clipboard')).catch(e=>alert('Clipboard write failed: '+e));return;}}}alert('No FPL token found - are you logged in?');}catch(e){alert('Error: '+e);}})();
```

Click it while on any fantasy.premierleague.com page you're logged into. The login dialog then
picks the token up automatically: it checks the clipboard when it opens and pre-fills (and
selects) the field if the contents look roughly like a bearer JWT, so the flow becomes click
bookmarklet -> switch to the app -> Log in, no manual copy-paste needed.

The token is encrypted with Windows DPAPI (tied to your Windows login) before being written to
`%LOCALAPPDATA%\FantasyFootball\fpl-token.dat`, so it persists across launches but is unreadable to
anyone without your Windows account. Use **Account -> Log out** to clear it and switch accounts -
the next login (automatic on the next launch, or via the menu) prompts for a fresh paste.

No session cookie is needed alongside the token - that was tried and confirmed unnecessary.

The token expires periodically; if a run fails with an authentication error, grab a fresh one from
dev tools and log out/back in via the Account menu. This is inherently a bit fragile since it relies
on an undocumented API and an unofficial way in - it can break if Premier League changes how their
frontend authenticates or stores its session.

### Stub mode

Set `FPL_MY_SQUAD_FILE` to the path of a squad JSON file (typically `my-squad.json`) to use
`FileSquadProvider` instead - no FPL login at all, real or stubbed:

```bash
FPL_MY_SQUAD_FILE=my-squad.json ./gradlew bootRun
```

Copy `my-squad.example.json` to that path and edit it:

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
isn't part of this file format. Since there's no live FPL entry to submit to, the "Make this
transfer" button is hidden entirely on every suggestion card in this mode - suggestions are
informational only.

If you have an older squad file with a flat `players` list, split it into `startingEleven` (first
11) and `substitutes` (last 4) - the split doesn't need to be a real formation. There's also no
Account menu in this mode, since there's no FPL account involved.
