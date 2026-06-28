# Verity Minecraft Mod

A Fabric mod that adds Verity, a mysterious talking sphere that follows you, reacts to you, and slowly turns your world into something stranger.

## Status
Alpha build for Minecraft 1.21.1.

## AI System

Verity uses a **Finite State Machine (FSM)** with **7 behavioral phases** based on the original ARG lore, integrated with **OpenRouter free LLM models** for dynamic dialogue.

### Phases

| Phase | Trigger | Behavior |
|---|---|---|
| `DORMANT` | Inside cardboard box | Waiting to be activated |
| `HELPER` | After box opens | Follows player, friendly chat |
| `OMNISCIENT` | ~2 min after HELPER | Reveals "knowledge" about player's real life |
| `COUNTDOWN` | Player walks 30+ blocks away | "3 days" countdown, telekinesis doors, stalking |
| `MONSTER` | Countdown completes | Transforms, chases player, breaks blocks |
| `POSSESSIVE` | Player sneaks near monster | Forgives player, isolates from others |
| `HUNTER` | Player attacks in POSSESSIVE / walks 40+ blocks | Aggressive hunt, eliminates other players |

### LLM Integration (OpenRouter)

- **Model:** `qwen/qwen3-next-80b-a3b-instruct:free` (free, excellent Russian/English)
- **Alternatives:** `meta-llama/llama-3.3-70b-instruct:free`, `liquid/lfm-2.5-1.2b-instruct:free`
- All requests are **async** (never blocks server tick)
- **Cache** for common responses (reduces API calls)
- **Fallback** to hardcoded phrases if no API key is set
- **Memory:** dialogue history + known facts about player (extracted from chat)

### Configuration

Create `config/verity-server.properties`:
```properties
# Get your free API key at https://openrouter.ai/keys
openrouter_api_key=sk-or-v1-xxxxxxxxxxxxx
openrouter_model=qwen/qwen3-next-80b-a3b-instruct:free
```

Without an API key, Verity uses cached/hardcoded phrases.

### Chat Integration

Verity listens to player chat messages. If a player mentions "verity", "верити", "шар", or asks questions (contains "?", "why", "кто", etc.) within 32 blocks, Verity responds via LLM.

## Build

```bash
./gradlew build
```

The built mod jar will be in `build/libs/`.

## Technical Details

- **Fabric** 1.21.1, Java 21, Mojang Mappings
- **FSM** stored in NBT (persists across world reloads)
- **EntityData sync** for phase/face/monster-form (server → client)
- **AI Goals** per phase: Follow, Stalk, MonsterAttack, OpenDoor (telekinesis)
- **BedrockMesh** loader for original `.geo.json` poly_mesh models
- **23 sounds** from original Bedrock addon
- **12 face states** with glow layer for creepy states
- **Bilingual:** English + Russian (auto-detects from player chat language)