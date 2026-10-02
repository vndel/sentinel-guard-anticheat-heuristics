# sentinel-guard-anticheat-heuristics

![Java](https://img.shields.io/badge/Java_21-ED8B00?logo=openjdk&logoColor=white) ![Paper](https://img.shields.io/badge/Paper_1.20%2B-0D7E84?logo=minecraft&logoColor=white) ![Maven](https://img.shields.io/badge/Maven-C71A36?logo=apachemaven&logoColor=white) ![License](https://img.shields.io/badge/License-MIT-green)

> Movement and combat telemetry with latency and TPS compensation. Collects evidence; does not punish.

## Scope

This raises violation levels and logs. **It does not kick or ban.**

That is a deliberate limit. Heuristics are probabilistic: under packet loss or a
TPS dip, any threshold produces false positives. Auto-banning on a heuristic
means banning laggy players, so the enforcement decision stays with an operator
or an external handler consuming the violation feed.

## The compensation problem

Without compensation, every check degenerates into a ping test. A player at
250ms fails reach checks that a 20ms player passes while doing exactly the same
thing, because the server sees the attacker's position *after* their movement
packet while the client aimed at where the target was one or more ticks earlier.
At 200ms that discrepancy alone is roughly a block.

```
threshold = base × leniency × (1 + latencyTolerance + tickTolerance)
```

Both tolerances are capped, so an intentionally bad connection cannot buy
unlimited slack. Below `suspend-below-tps` all checks stop: readings there are
not meaningful.

## Checks

| Check | Signal | Accounts for |
|---|---|---|
| `movement-speed` | Horizontal blocks/tick vs. sprint-jump baseline | Speed effects, ice, water, vehicles, gliding, teleport grace |
| `combat-reach` | Eye-to-hitbox distance at damage time | Latency, nearest hitbox point rather than entity origin |

Reach measures to the closest point of the bounding box. Measuring to the entity
origin over-reports against tall mobs and produces false flags on Endermen.

## Violation decay

Levels decay continuously (`decay-per-minute`). That is what separates a lag
spike from cheating: an isolated anomaly fades, while a sustained stream outruns
decay and accumulates.

## Configuration

```yaml
compensation:
  tolerance-per-100ms: 0.08
  max-tolerance: 0.60         # ceiling on latency slack
  mspt-budget: 45.0
  suspend-below-tps: 16.0     # below this, verdicts are not trustworthy

violations:
  decay-per-minute: 2

checks:
  movement-speed: { enabled: true, leniency: 1.15, ice-factor: 2.4 }
  combat-reach:   { enabled: true, leniency: 1.10 }
```

## Measured impact

| Scenario | Metric | Before | After |
|---|---|---|---|
| 500 move events/s | added MSPT | — | <0.4ms |
| Legitimate play, 250ms ping | false positives | frequent | none observed |
| Server at 14 TPS | checks | flagging | suspended |

Both listeners are `MONITOR` priority and `ignoreCancelled`, so they observe
outcomes without altering event flow.

## Build

```bash
mvn clean package
# target/sentinel-guard-anticheat-heuristics-1.0.0.jar
```

Drop the jar in `plugins/`, start the server once to generate `config.yml`,
then adjust and run `/sentinel reload` where supported.

## License

MIT — see [LICENSE](LICENSE).
