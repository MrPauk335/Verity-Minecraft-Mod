import re

path = r"d:\Projects\Java\Verity\src\main\java\net\verity\entity\VerityEntity.java"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# Мы заменим блок с creepyMsgs и friendlyMsgs
pattern = r'(String\[\] creepyMsgs = \{.*?\}\s*;.*?String\[\] friendlyMsgs = \{.*?\}\s*;.*?nearest\.sendSystemMessage\(Component\.literal\(msg\)\)\s*;.*?this\.teleportCooldown\s*=\s*\d+\s*;)'

replacement = """String[] creepyMsgs = {
                    "\\u00a7e<Verity\\u2122>\\u00a7r \\u042f \\u0442\\u0443\\u0442.",
                    "\\u00a7e<Verity\\u2122>\\u00a7r \\u041d\\u0435 \\u0443\\u0431\u0435\u0433\u0430\u0439.",
                    "\\u00a7e<Verity\\u2122>\\u00a7r \\u042f \\u0432\u0438\u0436\u0443 \\u0442\u0435\\u0431\u044f.",
                    "\\u00a7e<Verity\\u2122>\\u00a7r \\u041a\u0443\u0434\u0430 \\u0442\u044b \\u0441\u043e\u0431\u0440\u0430\u043b\u0441\u044f?"
            };
            msg = creepyMsgs[this.random.nextInt(creepyMsgs.length)];
        } else {
            String[] friendlyMsgs = {
                    "\\u00a7e<Verity\\u2122>\\u00a7r \\u041e, \\u044f \\u0442\u0443\u0442!",
                    "\\u00a7e<Verity\\u2122>\\u00a7r \\u041f\u043e\u0434\u043e\u0436\u0434\u0438 \\u043c\u0435\u043d\u044f!",
                    "\\u00a7e<Verity\\u2122>\\u00a7r \\u042d\u0439, \\u043d\u0435 \\u0442\u0430\u043a \\u0431\u044b\u0441\u0442\u0440\u043e!",
                    "\\u00a7e<Verity\\u2122>\\u00a7r \\u042f \\u0434\u043e\u0433\u043d\u0430\u043b!"
            };
            msg = friendlyMsgs[this.random.nextInt(friendlyMsgs.length)];
        }
        nearest.sendSystemMessage(Component.literal(msg));
        this.talkAnimTick = 30;
        this.teleportCooldown = 200;"""

new_content, count = re.subn(pattern, replacement, content, flags=re.DOTALL)
print(f"Replaced {count} occurrences")

if count > 0:
    with open(path, "w", encoding="utf-8") as f:
        f.write(new_content)
