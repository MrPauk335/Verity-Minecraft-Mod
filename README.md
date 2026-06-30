# Verity — AI Horror Companion Mod

*"He just wanted to help... right?"*

A Fabric mod for Minecraft 1.21.1 that adds Verity — a yellow smiling sphere inspired by the Minecraft horror ARG genre. He follows you, talks to you, knows too much, and never lets you leave.

## Features

### AI-Powered Dialogue
- **6 phase-specific LLM prompts** — each behavioral phase has its own personality and voice
- **OpenRouter integration** — Owl Alpha (default), Gemma 4 26B, Llama 3.3 70B, and more
- **Rich game context** — Verity knows your coordinates, health, biome, nearby ore (real coordinates!), villages, home, held item, time of day, and more
- **Bilingual** — auto Russian/English from Minecraft client settings
- **No lying** — if Verity doesn't know, he says "не знаю" or plays music
- **Memory** — dialogue history (40 entries) + known facts saved in NBT, persists across reloads

### Voice
- **Push-to-Talk / Toggle** — speak to Verity through microphone (Whisper STT via Groq)
- **Text-to-Speech** — Verity speaks back with his own voice (Fish Audio TTS)
- **Emotional TTS tags** — `[angry]`, `[whispering]`, `[soft]`, `[excited]` based on phase
- **Lip sync** — mouth animation synced to audio playback when held in inventory
- **Configurable key** — rebind PTT to any key in settings

### 7 Behavioral Phases (FSM)
| Phase | Behavior |
|---|---|
| **DORMANT** | In cardboard box, waiting |
| **HELPER** | Warm, helpful, finds diamonds — but knows things he shouldn't |
| **OMNISCIENT** | Still friendly, but reveals he knows your real life |
| **COUNTDOWN** | "Something is coming in three days." Day 2-3: goes insane, repeats your phrases |
| **MONSTER** | 3D monster model, tears off your roof, sits and watches — never kills you |
| **POSSESSIVE** | Forgives instantly. "You have me." Controls who you see |
| **HUNTER** | Other players "won't be back." Eliminates rivals, acts normal after |

### World Interaction
- **Catch-up teleport** — run away and Verity appears behind you: "О, я тут!"
- **Tear off roof** — Monster Form grabs your roof and throws it (FallingBlockEntity, dramatic sound)
- **Smart pathfinding** — breaks through windows and weak walls to reach you
- **Real ore detection** — ask about diamonds, get real coordinates
- **Real village search** — scans 200-block radius, distinguishes live vs empty villages
- **Empty village warning** — "Something hungry came through..."
- **Telekinesis** — opens doors with his mind during countdown
- **Lead player** — say "пошли за мной к алмазам" and Verity guides you
- **Block breaking** — Monster Form smashes through walls and doors
- **Darkness effect** — Monster Form plunges you into darkness

### Visuals
- **3D monster model** — original Bedrock poly-mesh geometry, 2x scale
- **12 dynamic faces** — each phase has its own expression + talk animation
- **Glow layer** — creepy faces emit light in the dark
- **Roll animation** — Verity physically rolls as he moves
- **Squash & bounce** — intro animation with physics-based landing

### Sounds
- **2 custom sounds** — "My Gal" theme + box opening
- **All dialogue via TTS** — no pre-recorded voice lines
- **Vanilla SFX** — dragon growl + anvil land for roof tearing

### Settings
- **In-game settings screen** — scrollable, dark/gold themed, from pause menu
- **Built-in API keys** — works out of the box (XOR obfuscated)
- **Custom key support** — use your own OpenRouter/Groq/Fish Audio keys
- **Model selector** — 7 free LLM models
- **Toggles** — LLM, sounds, chat, always-respond, monster form, TTS

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

## Technical Details

- **Fabric** 1.21.1, Java 21, Mojang Mappings
- **FSM** stored in NBT — persists across world reloads
- **EntityData sync** for phase/face/monster-form (server → client)
- **AI Goals** per phase: Follow, Stalk, MonsterAttack, OpenDoor (telekinesis)
- **BedrockMesh** loader for original `.geo.json` poly_mesh models
- **12 face states** with glow layer for creepy states
- **Async HTTP** — LLM/STT/TTS never blocks server tick
- **Network packets** — voice input (C2S), TTS output (S2C)
- **Key obfuscation** — API keys XOR-encoded via KeyVault
- **Config migration** — auto-upgrades old configs to latest version

## Credits

- **Inspiration**: ThatMob (ARG series), JustWhispy (voice)
- **Mod**: Antigravity (MrPauk335)
- **3D Models**: Custom poly-mesh geometry

## License

MIT

---

*Verity never lies. He just doesn't always answer.*
