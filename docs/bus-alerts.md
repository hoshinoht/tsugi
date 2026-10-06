# Bus alerts

Tap the bell on a bus in a stop's screen to track it. One bus is tracked at a time; tracking another replaces it.

## What you get

| Notification | Channel | Behaviour |
| --- | --- | --- |
| Live countdown | Live bus tracking (low importance) | Ongoing; title like "12 in 4 min", destination, stop, crowding and the next bus. Tap to open the stop; **Stop** ends tracking. |
| Approaching | Bus alerts (high importance) | Heads-up with vibration when the bus is 2 min away. |
| Arriving | Bus alerts (high importance) | Heads-up when the bus is under a minute away. |

On Android 16 QPR2 (API 36.1) and later the countdown is promoted to a **Live Update**: a progress bar with a bus marker, a status-bar chip showing the minutes, and lock-screen placement. On Android 16 it shows the progress bar only; earlier versions show a standard ongoing notification.

## Timing

| Rule | Value |
| --- | --- |
| Poll interval | 20 s, or 60 s while the bus is more than 10 min away |
| Stops after arrival | 2 min after the arriving alert |
| Maximum tracking time | 90 min |
| Gives up without live info | after 10 min, with a notification saying so |

## Permissions

| Permission | Why |
| --- | --- |
| `POST_NOTIFICATIONS` | Asked the first time you tap a bell (Android 13+) |
| `POST_PROMOTED_NOTIFICATIONS` | Lets the countdown become a Live Update |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keeps tracking alive in the background; the special-use type applies from Android 14 |

The service doesn't restart itself if the system kills it, since a stale countdown is worse than none.
