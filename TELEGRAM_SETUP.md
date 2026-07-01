# Telegram Bot Setup Guide

## 1. Create a Telegram Bot

1. Open [@BotFather](https://t.me/BotFather) in Telegram
2. Send `/newbot`
3. Choose a name (e.g. "Verity Mod Releases")
4. Choose a username (e.g. `verity_mod_releases_bot`)
5. Copy the **Bot Token** (looks like `123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11`)

## 2. Get your Channel Chat ID

1. Create a Telegram channel (or use existing)
2. Add the bot as **administrator** to the channel (required for posting)
3. Forward a message from your channel to [@userinfobot](https://t.me/userinfobot)
4. Copy the **Chat ID** (looks like `-1001234567890`)

## 3. Add secrets to GitHub

1. Go to: https://github.com/MrPauk335/Verity-Minecraft-Mod/settings/secrets/actions
2. Click **New repository secret**
3. Add `TG_BOT_TOKEN` = your bot token from step 1
4. Add `TG_CHAT_ID` = your chat ID from step 2

## 4. Done!

When you create a GitHub release (like `v0.8.0-beta`), the bot will automatically post:
- Release title
- Release notes (changelog)
- Download link
- GitHub link

To a Telegram channel.
