# Verity Mod — Changelog

## v0.8.2-beta

### Audio & TTS
- **Native OpenAL playback** — moved TTS audio output from external programs (PowerShell, mpg123, afplay, cvlc) to Java-native OpenAL (via LWJGL) which is built into Minecraft. Now works out of the box on Windows, Linux, macOS, FreeBSD, and Android (Zalith/Pojav).
- **Lossless WAV integration** — requests WAV format from Fish Audio, parsing standard PCM data dynamically in-memory for instant playback without local file caching/decoding.

### Fixes
- **No duplicate messages** — added message deduplication to prevent double/triple chat messages and TTS triggers on integrated/LAN servers.
- **UTF-8 character encoding** — forced UTF-8 response parsing for LLM and STT HTTP clients, resolving issues with broken Cyrillic letters (`Р§С‚Рѕ-С‚Рѕ...`) on Windows.

## v0.8.1-beta

### Fixes
- **Config saving fixed** — config_version was stuck at 4, causing migration loops that overwrote user's provider choice
- **Provider respects user choice** — OpenRouter/Gemini selection now persists correctly
- **Removed broken builtin Gemini keys** — were causing 15s timeouts
- **503 retryable** — Gemini and OpenRouter now try next key/model on 503 (high demand)
- **Timeout reduced** 15s → 8s for faster fallback
- **Removed aggressive gibberish filter** — was rejecting valid short responses like "Ты не один. Я рядом."
- **Anti-leak filter** — rejects Gemini responses that output system prompt reasoning
- **UTF-8 encoding** — all non-ASCII in Java string literals and text blocks converted to \uXXXX escapes

### Security
- **Encrypted API keys** — keys stored as `enc:Base64` (XOR cipher) in config file
- **Masked key fields** — all API key inputs show `********` in settings screen

### UI/UX
- **Bilingual welcome message** — auto-detects Russian/English from Minecraft client language
- **Bilingual /verity help** — full RU/EN support with commands, interaction, AI/LLM info, key setup guides
- **Provider selector** in settings — switch between Gemini and OpenRouter
- **5 Gemini models** — gemini-3.1-flash-live-preview, gemini-3-flash-preview, gemini-2.5-flash, gemini-2.5-flash-lite, gemma-4-31b-it
- **TTS enabled by default**
- **Telegram bot** — auto-posts releases to Telegram channel via GitHub Actions

## v0.8.0-beta

### Ball Physics
- **Kick Verity** — left-click to kick Verity like a ball (bouncing, rolling, wall bounce)
- **Throw from inventory** — right-click in air with Verity item throws it as a ball
- **Q-drop throw** — press Q with Verity item to throw it as a ball entity
- **Pick up** — right-click on Verity entity to pick up to inventory
- **Bounce physics** — gravity, ground bounce (0.55x damping), wall bounce, rolling friction
- **Knockback** — flying Verity pushes entities it hits
- **Settle** — Verity stops and returns to AI when speed is low
- **No fall damage** — Verity is immune to fall damage while thrown
- **AI disabled** while thrown — no following, no teleport, no stalking

### Gemini API Support
- **3 built-in Gemini keys** — works out of the box, no 429 like OpenRouter
- **Provider selector** in settings — switch between Gemini and OpenRouter
- **5 Gemini models** — gemini-3.1-flash-live-preview, gemini-3-flash-preview, gemini-2.5-flash, gemini-2.5-flash-lite, gemma-4-31b-it
- **Key rotation** — cycles through 3 builtin + custom Gemini keys on rate limit
- **Anti-leak filter** — rejects responses where model outputs system prompt reasoning
- **Default provider** — Gemini (no more OpenRouter 429 spam)
- **Config migration v5→v6** — auto-switches to Gemini provider

### Monster Form
- **No longer kills player** — Monster now scares (follows, heartbeat sound) but deals 0 damage
- **VerityScareGoal** replaces MeleeAttackGoal — canonical "scares, doesn't kill"

### TTS
- **Enabled by default** — TTS is now on out of the box
- **Linux MP3 playback** — tries mpg123 → ffplay → cvlc fallback chain

### Fixes
- **Verity indestructible** — immune to cactus, fall, drown, suffocation, explosions (only fire/lava triggers rage)
- **Rotation fixed** — ball no longer looks at floor/sky when walking (removed headPitch from xRot)
- **"Выключи песню"** — now stops music instead of playing it
- **UTF-8 encoding** — all § replaced with \u00A7, ™ with \u2122 in source files
- **My Gal sound** — stream:true in sounds.json for large OGG file

## v0.7.0-beta

### Horror (Verity itself is scary now)
- **Darkness pulses** — brief Darkness effect when standing near Verity (OMNISCIENT+)
- **Face glitch** — face randomly switches to creepy/abnormal for 3-8 ticks, then back
- **Staring** — Verity stops following and stares at player for 5-10 seconds
- **Whispers** — random creepy messages without LLM: "...ты один?", "...я слышу тебя", "...скоро", "...три"
- **Doors open themselves** — doors in 4-block radius open randomly (OMNISCIENT+, not just COUNTDOWN)
- **My Gal at night** — music plays softly at night without request
- **Sleep teleport** — after sleeping, Verity may appear next to bed with Darkness effect
- **Nearby weakness** — short Darkness when player gets within 3 blocks

### AI / LLM
- **Dead model replaced** — `openrouter/owl-alpha` (404) → `meta-llama/llama-3.3-70b-instruct:free`
- **Fallback chain reordered** — llama-3.3-70b → nemotron-3-super-120b → qwen3-next-80b → gemma-4-26b
- **Canonical prompts** — all 6 phase prompts rewritten to match ThatMob's original Verity speech patterns
- **Max 15 words per response** — Verity speaks short and flat like canon ("Yes", "You could've", "You have me")
- **Response quality filter** — rejects short (<5 chars), gibberish, and truncated responses
- **Emoji & bracket tag stripper** — removes 🔍, [excited], [soft] etc. from LLM output
- **Canonical fallback phrases** — when all models rate-limited, Verity says phase-appropriate canon lines
- **max_tokens 150 → 256** — prevents truncated responses on weaker models

### Fixes
- **My Gal sound** — now plays at player position instead of entity position (was inaudible when Verity far)
- **Config migration v4→v5** — auto-replaces owl-alpha, updates fallback list, increases max_tokens

### Technical
- **7 available models** — llama-3.3-70b, nemotron-3-super-120b, qwen3-next-80b, qwen3-coder, gpt-oss-120b, hermes-3-405b, gemma-4-26b
- **Settings screen updated** — new model display names

## v0.6.0-beta

### Monster Form
- **3D monster model** — original Bedrock poly-mesh, 2x scale, lazy loaded with error logging
- **Monster sits and watches** — no longer chases, stays in place, only looks at player
- **Tear off roof** — detects walls → climbs → flood-fill (including overhangs) → blocks fly as one object (NoGravity 1.5s → gravity + throw), sound: ENDER_DRAGON_GROWL + ANVIL_LAND
- **Smart pathfinding** — breaks glass/weak blocks (hardness < 3.0) when path blocked, checks front + sides
- **No Thread.sleep** — roof gravity via tick timer (30 ticks), no server freeze

### AI / LLM
- **Default model** — best free Russian model as default
- **Fact checking in prompt** — rule 9: verify every statement against context, rule 10: don't invent
- **Male gender fix** — "я рад" not "я рада", rule 11
- **Day 2-3 insanity** — repeats player's phrases (echo), "Три дня." / "Тик-так." / "Скоро.", less frequent
- **Village fix** — checks actual villagers first before saying "empty", removed false "где/куда" triggers

### Voice / TTS
- **Emotion tags** — [angry] [whispering] [soft] [excited] [groaning] [panting] by phase
- **Lip sync fix** — mouth opens 300ms after audio starts (not on request), closes when audio ends
- **TTS speed by phase** — MONSTER 0.85x, COUNTDOWN 0.9x, normal 1.0x + temperature 0.5 for dark phases

### World
- **Player home detection** — crafting table + furnace + chest + bed + door + glass, 3+ types = home
- **Lead player** — "пошли за мной к алмазам/дом/деревня" → Verity guides, waits if player falls behind
- **Stop leading** — "стой" only triggers when Verity is actually leading

### Commands (17 total)
- `/verity roof` — tear off roof
- `/verity say <text>` — make Verity say text with TTS
- `/verity monster` — toggle monster form
- `/verity teleport` — teleport Verity to player
- `/verity music` — play My Gal
- `/verity lead <target>` — lead to ore/home/village
- `/verity stop` — stop leading
- `/verity reset` — reset to HELPER
- `/verity clear` — remove all Verity
- `/verity spawn` — create new Verity
- `/verity day <0-3>` — set countdown day

### Technical
- **Config migration v4** — auto-upgrade model settings
- **Duplicate tick() removed** — was causing compile errors
- **README.md rewritten** — full feature list, commands, credits
- **fabric.mod.json** — MIT license, better description, GitHub links

## v0.5.0-beta
- **6 phase-specific system prompts** — each phase has its own personality and voice
- **No hardcoded phrases** — all dialogue generated by LLM, removed MSG_* arrays and canonical cache
- **No lying** — prompt explicitly forbids fabrication; if Verity doesn't know, he says "не знаю" or plays music
- **No asterisk actions** — `*жёлтый шар подпрыгивает*` stripped from LLM responses and TTS
- **No emojis** — explicitly forbidden in prompt
- **Real ore detection** — scans 20-block radius, gives REAL coordinates for diamonds/gold/iron/etc.
- **Real village search** — scans 200-block radius using village-only markers (bell, lectern, composter, job blocks, dirt paths)
- **Empty village detection** — detects villages with no villagers, warns "something hungry came through"
- **Rich context** — player coordinates, home/bed, health, hunger, held item, biome, dimension, depth, time of day, nearby players, countdown day, known facts
- **Language detection** — auto Russian/English from Minecraft client settings
- **Contextual auto-dialogue** — Verity speaks only when there's a real reason (player hurt, night, underground, day change, rival nearby) — no more random spam
- **Music request** — say "включи музыку" / "play music" / "my gal" — bypasses cooldown, plays instantly
- **Gemma 4 26B default model** — faster (2-5s) than Owl Alpha (4-25s), owl-alpha as fallback
- **Config migration v3** — auto-upgrades to gemma
- **Timeout reduced** — 15s → 10s

### Voice
- **Push-to-Talk with configurable key** — default V, rebindable in settings
- **Toggle mode** — alternative to push-to-talk
- **Whisper STT via Groq** — 6 built-in API keys with rotation, whisper-large-v3
- **"Вирити" recognition** — STT prompt hint for correct name recognition
- **TTS via Fish Audio** — 3 built-in API keys, Verity's voice, model s2.1-pro-free
- **TTS toggle in settings**
- **Talks when held in inventory** — LLM dialogue + TTS works when Verity is picked up
- **Lip sync animation when held** — mouth opens/closes in speech pattern synced to audio playback

### Settings Screen
- **Complete redesign** — dark panel, gold borders, section headers, scrollable
- **Key source toggle** — Built-in keys vs Custom keys
- **Model selector** — 7 models with friendly names
- **5 server toggles** — LLM, Sounds, Chat, Always Respond, Monster Form
- **PTT key picker** — click to rebind, press any key
- **Voice mode cycle** — Push-to-Talk / Toggle
- **TTS section** — toggle + API key field
- **Settings persist** — forceReload fixes config not applying after save
- **Pause screen button** — centered, gold, below vanilla buttons

### Faces (all 12 used)
- **Phase-specific face mapping** — each phase has default face + talk animation pair
- **COUNTDOWN progression** — day 0: :| (5) → day 1: abnormal shut (3) → day 3: day2 shut (6)
- **HUNTER dual face** — calm :| (5) when idle, screaming (11) when chasing
- **OMNISCIENT** — black tired eyes (9), talks with serious-2 (10)
- **POSSESSIVE** — flat :| (5), talks with speak (1)
- **MONSTER** — creepy smile (8), talks with day2 open (7)
- **onPhaseEnter** — face set automatically on phase transition
- **COUNTDOWN day change** — face updates per day

### Behavior
- **Catch-up teleport** — if player >32 blocks away, teleports ~10 blocks behind with wall-avoidance, says "О, я тут!" or "Не убегай."
- **No overlap** — teleport suppressed during LLM dialogue
- **Village markers** — bell/lectern/composter/job blocks/dirt paths (not beds/doors)
- **Chat radius** — 64 blocks
- **Always Respond toggle** — responds to all chat without trigger words
- **Held item dialogue** — chat + voice work when Verity in inventory
- **Held data persistence** — phase/history/facts saved when picked up, restored when placed

### Memory
- **NBT persistence** — dialogue history (40 entries) and known facts saved/loaded
- **No more "доброе утро" on every join** — Verity remembers previous conversations

### Rendering
- **Removed old textures** — verity_face_blank.png, verity_creepy.png, verity_friendly.png deleted
- **Monster form on sphere** — creepy smile texture, not 3D monster model
- **Held item animation** — squish pulse + mouth open/close synced to TTS audio

### Sounds
- **Removed 21 ARG sound files** — only 2 remain: mygal_normal (My Gal theme) + punchcardboardbox (box opening)
- **All dialogue via TTS** — no more pre-recorded voice lines
- **My Gal** — plays on request via chat/voice

### Security / Legal
- **Key obfuscation** — API keys XOR-encoded via KeyVault, not visible in plaintext
- **Debug scripts removed** — no plaintext keys in repository
- **MIT license** — in fabric.mod.json
- **About text updated** — "Inspired by" instead of "Based on", no direct asset claims

### Technical
- **TTSPayload** — S2C network packet for voice output
- **Config version 3** — migration system
- **Singleton enforcement** — one Verity at a time
- **Gemma 4 26B** — faster default model, 10s timeout, owl-alpha fallback
- **Held static storage** — phase/history/facts stored when entity discarded

### Known Issues
- TTS playback Windows-only (PowerShell MediaPlayer)
- Some config options defined but not wired (speed, timing)
