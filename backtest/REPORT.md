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
