# Backtest: Meme Tail Lab strategy on Bybit spot new listings

**Period:** Bybit USDT spot listings, Sep 2024 – Aug 2026 (261 tokens)
**Data:** Bybit public trade archive (every trade from the first trade on)
**Settings:** the app's defaults, $10 per trade from a $1,000 paper book

## Result

| | |
|---|---|
| Listings tested | 261 |
| Qualified and traded | 151 |
| Skipped (buy/sell < 1.1 / momentum < 0 / too few trades) | 66 / 41 / 3 |
| Win rate | 24.5% (37 wins, 114 losses) |
| Total P/L | **+$19.31** on $1,510 staked (+1.9% on the $1,000 book) |
| Profit factor | 1.06 |
| Median trade | −21.4% |
| Max drawdown | −$128.75 |
| Exits | 76 at the 48h limit, 72 stopped out, 3 via the runner trail |

**Without the 3 best trades (MMT +641%, SKR +539%, HOODX +457%) the result is −$144.**

By listing year: 2024 −$58.55 (36 trades), 2025 +$26.28 (86), 2026 +$51.57 (29).

## Reading it

- The strategy is roughly break-even: most trades lose 20–35%, and a few rare 8–12x runs pay for them.
- Only 3 of 151 trades (2%) reached the 7x level where the partial-sell and trailing-stop rules start.
- Half the stop-outs happen within about an hour of entry (median 63 min).
- Because the result depends on a handful of outliers, missing one of them (being offline, a slow fill) would turn the total negative.

## How the app's rules were applied

- Qualification checked every 10s between 5 and 25 minutes after the first trade: at least 20 trades, at least 20 buys, buy/sell ≥ 1.1, and price momentum since launch ≥ 0%.
- Fill 15s after the signal at the last trade price plus 1.5% slippage.
- Exits checked every 20s. The stop starts at 0.675x and ratchets to 1.5x/2x/2.5x after 2x/3x/5x. The strategy sells 10% at 7x and 2.5% at 20x, trails by 32.5% after 7x, and closes everything at 48h. Exits pay 2% slippage plus $0.03.

**Differences from the app** (Bybit is an exchange order book, not a DEX pool):
no liquidity filter or price-impact model, buys stand in for unique buyers,
and trade prices replace DexScreener quotes.

## Reproduce

```
python3 listings.py              # map each token's first archive month
python3 backtest.py 2024-09 2026-08
```

Per-trade details are in `results.json`.

---

# Tuning study (Dec 2022 – Aug 2026, 544 listings)

Tools: `fetch_bars.py` (cache first 49h as 10s bars, listing start = first
60s window with 20+ trades), `tune.py` (fast replay), `study.py` (moonshot
predictors), `search.py` (grid search: pick settings on listings before 2025,
check them on 2025–26 listings they never saw).

## How often do Bybit listings moon?

From the price 5 minutes after trading opens, within 48h: 41 of 544 reached
2x, 16 reached 3x, 7 reached 5x, 3 reached 10x. The typical listing ended 48h
later at 0.81x.

Early metrics vs reaching 3x (quintiles at 5 min; small counts, so treat as hints):

- Buy share of USD volume ≥ 62%: 7% hit 3x vs 1% when < 47% (strongest signal)
- Trading within 3% of the early high: 7% vs 1–3%
- Quieter listings (< ~$290k traded in 5 min): 5–6% vs 1% for the busiest
- Early momentum alone: no signal

## Exits

Tighter stops (0.9x first stop, time stop for non-movers) halve the average
loss ($2.96 → $1.42) but cut the moonshots before they run: pre-2025 improves
−$357 → −$167, while 2025–26 drops from +$82 to about −$100. The app's loose
0.675x stop is what lets the rare runners survive, so it should stay.

## Entries

No filter combination was profitable before 2025; the best only lost less by
trading less. The setting that held up best out of sample:

**average trade ≥ $150 at entry, skip if already up > 100%, entry window 5–60 min**

| | pre-2025 (train) | 2025–26 (test) |
|---|---|---|
| App defaults (DEX costs) | 183 trades, −$356.86 | 114 trades, +$82.27 |
| App defaults (Bybit costs*) | 183 trades, −$310.76 | 114 trades, +$119.35 |
| Filtered (DEX costs) | 32 trades, −$31.46 | 33 trades, +$30.81 |
| Filtered (Bybit costs*) | 32 trades, −$22.54 | 33 trades, +$41.73 |

\* 0.4% slippage/fee each way instead of the app's DEX 1.5% in / 2% out + $0.03.

The filtered 2025–26 result still depends on one trade (HOODX +$46).

## Conclusion

Bybit spot listings are vetted, large-cap launches that mostly drift down;
they are not the early meme-coin launches the strategy was built for. Early
Bybit trade-flow metrics carry only weak signals, and the results swing with
the market period. A fair test of the meme strategy needs DEX launch data
(liquidity, unique buyers, holder counts) from the venues the app trades.

---

# Rising-activity entry (`activity.py`)

Idea: only enter when activity is building. There is no wallet data on Bybit,
so wallet growth is approximated by trade-count growth and money flow by USD
volume growth, comparing the last W minutes with the W minutes before.
Grid: W 5/15/30 min, trade growth 0/20/50%, volume growth 20/50/100%, buy share,
minimum window volume, momentum, distance from high, entry up to 6h. App exits.

Best setting chosen on pre-2025 listings:

**W = 15 min: trades up ≥ 20% and USD volume up ≥ 20% vs the previous 15 min,
buy share ≥ 52% in the window, ≥ $20k traded in the window, price above the
listing open; entry 30–60 min after listing** (30 min is the earliest a 15+15
min comparison is possible).

| | pre-2025 (train) | 2025–26 (test) |
|---|---|---|
| App defaults | 183 trades, −$356.86 | 114 trades, +$82.27 |
| Rising activity (DEX costs) | 30 trades, −$13.26 | 30 trades, +$59.79 (win 43%, PF 2.68) |
| Rising activity (Bybit costs) | 30 trades, −$4.46 | 30 trades, +$70.60 (win 43%, PF 3.24) |

- Losses are smaller: median trade −13% / −5% vs −35% / −20% with the defaults.
- Robustness: across the top 50 settings picked on pre-2025 data, the 2025–26
  P/L had a median of +$36.72 (range −$1.30 to +$89.70).
- Still not profitable before 2025, and 2025–26 relies on one runner
  (MMT +$52.5); without the top 3 trades 2025–26 is −$10.38.

---

# Runner-safe filters (`runners.py`, `runner_safe.py`)

The rising-activity rule was too tight: it caught 6 of 16 runners (listings that
reached ≥ 3x from their 5-minute price within 48h), because it waits 30 min and
most runners move in the first minutes. The app's default entry caught 15/16,
almost all at 5 min. So the search was redone with a different objective: keep
the early entry, and only accept settings that still catch at least 6 of the 7
pre-2025 runners; among those, pick the smallest pre-2025 loss.

At the app's entry point, runners vs the rest (medians): buy share 0.64 vs 0.57
(never below 0.51), USD traded so far $128k vs $416k, 2.5% vs 10% below the
early high, last-5-min move +14% vs +2%.

Best setting (app filters plus):

**entry from 1 min (instead of 5), buy share of USD volume ≥ 58%, ≤ $500k
traded so far, not more than 50% below the early high, not down more than 10%
in the last 5 minutes**

| | pre-2025 | 2025–26 (unseen) |
|---|---|---|
| App defaults | 183 trades, −$356.86, runners 6/7 | 114 trades, +$82.27, runners 9/9 |
| Runner-safe | 109 trades, −$160.41, runners 6/7 | 79 trades, +$66.85, runners 8/9 |
| Rising activity | 30 trades, −$13.26, runners 3/7 | 30 trades, +$59.79, runners 3/9 |

- Cuts pre-2025 losses by more than half while keeping the same runners.
- 2025–26 misses only TRUMP (+$9 under the app's exits); median trade −12%
  vs −20% with the defaults.
- Across the top 50 runner-safe settings, 2025–26 P/L: median +$47.18
  (range +$5.27 to +$69.86).
- A looser volume cap ($1M / $2M) keeps more trades but loses more:
  pre-2025 −$213 / −$238, 2025–26 +$47 / +$43.
