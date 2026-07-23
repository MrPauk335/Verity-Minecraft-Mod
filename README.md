# Verity — AI Horror Companion Mod

*"He just wanted to help... right?"*

[![Telegram](https://img.shields.io/badge/Telegram-Channel-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/VerityMod228)
[![GitHub Release](https://img.shields.io/github/v/release/MrPauk335/Verity-Minecraft-Mod?style=for-the-badge&color=gold)](https://github.com/MrPauk335/Verity-Minecraft-Mod/releases)

A Fabric mod for Minecraft 1.21.1 that adds Verity — a yellow smiling sphere inspired by the Minecraft horror ARG genre. He follows you, talks to you, knows too much, and never lets you leave.

📢 **Official Telegram Channel (ТГ-канал)**: [https://t.me/VerityMod228](https://t.me/VerityMod228)

---

## Features

### AI-Powered Dialogue
- **6 phase-specific LLM prompts** — each behavioral phase has its own personality and voice
- **OpenRouter & Gemini integration** — Owl Alpha (default), Gemma 4 26B, Llama 3.3 70B, Gemini 2.0, and more
- **Rich game context** — Verity knows your coordinates, health, biome, nearby ore (real coordinates!), villages, home, held item, time of day, and more
- **Bilingual** — auto Russian/English from Minecraft client settings
- **No lying** — if Verity doesn't know, he says "не знаю" or plays music
- **Memory** — dialogue history (40 entries) + known facts saved in NBT, persists across reloads

### Voice & STT/TTS
- **Push-to-Talk / Toggle** — speak to Verity through microphone (Whisper STT via Groq)
- **Text-to-Speech** — Verity speaks back with his own voice (Fish Audio TTS)
- **STT Command Vocabulary** — trained speech prompt recognizing Minecraft voice commands ("снеси дом", "телепортируйся", "убей жителей")
- **Emotional TTS tags** — `[angry]`, `[whispering]`, `[soft]`, `[excited]` based on phase
- **Clean Dialogue Formatting** — automatic prefix stripper removes duplicate "Verity" tags in chat & voice synthesis
- **Lip sync & Head rotation** — pitch/yaw head rotation up to 80° (looks up at players on pillars!)

### 7 Behavioral Phases (FSM)
| Phase | Behavior |
|---|---|
| **DORMANT** | In cardboard box, waiting |
| **HELPER** | Warm, helpful, finds diamonds — but knows things he shouldn't |
| **OMNISCIENT** | Still friendly, but reveals he knows your real life |
| **COUNTDOWN** | "Something is coming in three days." Day 2-3: goes insane, repeats your phrases |
| **MONSTER** | 3D monster model, tears off your roof, sits and watches — ruthless chaser |
| **POSSESSIVE** | Forgives instantly. "You have me." Controls who you see |
| **HUNTER** | Other players "won't be back." Eliminates rivals, acts normal after |

### World Interaction & Mob Operations
- **Mob ChunkChecker** — scans 32m radius around player, audits entity counts, passive animals, and warns of nearby threats
- **Universal & Mass Execution** — order Verity to kill specific mobs, villagers, or all surrounding entities with dark magic FX
- **Physical House Demolition** — commands like "снеси", "снести", "сносить", "убери крышу/дом/хату" physically tear down structures
- **Smart Long-Distance Navigation** — dynamic 24-block surface waypoints guide player to villages even 1000+ blocks away
- **Catch-up Teleport on Death/Respawn** — extended 8192-block search range teleports Verity right back to you when you respawn
- **Tear off roof** — Monster Form grabs your roof and throws it (FallingBlockEntity, dramatic sound)
- **Smart pathfinding** — breaks through windows and weak walls to reach you
- **Real ore detection** — ask about diamonds, get real coordinates
- **Empty village warning** — "Something hungry came through..."

### Visuals
- **High-poly 3D Monster model** — authentic Bedrock poly-mesh geometry, articulated bones and pitch rotation
- **12 dynamic faces** — each phase has its own expression + talk animation
- **Glow layer** — creepy faces emit light in the dark
- **Roll animation** — Verity physically rolls as he moves
- **Squash & bounce** — intro animation with physics-based landing

### Settings
- **In-game settings screen** — scrollable, dark/gold themed, from pause menu
- **Built-in API keys** — works out of the box (XOR obfuscated)
- **Custom key support** — use your own OpenRouter/Groq/Fish Audio keys
- **Model selector** — 7 free LLM models

### Commands
```
/verity                    — help
/verity info               — mod status
/verity phase              — current phase
/verity phase set <phase>  — set phase (HELPER, MONSTER, etc.)
/verity toggle <option>    — toggle: llm, chat, sounds, monster, teleport, eat, doors
/verity face <0-11>        — set face
/verity monster            — toggle monster form
/verity roof               — tear off roof
/verity say <text>         — make Verity say text (with TTS)
/verity teleport           — teleport Verity to you
/verity music              — play "My Gal"
/verity lead <target>      — lead player to ore/home/village
/verity stop               — stop leading
/verity reset              — reset to HELPER
/verity clear              — remove all Verity entities
/verity spawn              — spawn new Verity
/verity day <0-3>          — set countdown day
```

## Build

```bash
./gradlew build
```

The built mod jar will be in `build/libs/`.

## Community & Links

- **Telegram Channel (ТГК)**: [https://t.me/VerityMod228](https://t.me/VerityMod228)
- **GitHub Repository**: [https://github.com/MrPauk335/Verity-Minecraft-Mod](https://github.com/MrPauk335/Verity-Minecraft-Mod)
- **Releases & Downloads**: [https://github.com/MrPauk335/Verity-Minecraft-Mod/releases](https://github.com/MrPauk335/Verity-Minecraft-Mod/releases)

## Technical Details

- **Fabric** 1.21.1, Java 21, Mojang Mappings
- **FSM** stored in NBT — persists across world reloads
- **EntityData sync** for phase/face/monster-form (server → client)
- **Async HTTP** — LLM/STT/TTS never blocks server tick
- **Network packets** — voice input (C2S), TTS output (S2C)
- **Key obfuscation** — API keys XOR-encoded via KeyVault

## Credits

- **Inspiration**: ThatMob (ARG series), JustWhispy (voice)
- **Mod Creator**: Antigravity (MrPauk335)
- **Community**: [t.me/VerityMod228](https://t.me/VerityMod228)

## License

MIT

---

*Verity never lies. He just doesn't always answer.*
