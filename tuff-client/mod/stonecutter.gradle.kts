plugins {
    id("dev.kikugie.stonecutter")
}

// The version the code in src/ is written for. Other versions get a converted copy.
stonecutter active "26.3"

stonecutter parameters {
    replacements {
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }
        string(current.parsed >= "26.1") {
            replace("net.minecraft.client.GuiMessage", "net.minecraft.client.multiplayer.chat.GuiMessage")
        }
    }
}
