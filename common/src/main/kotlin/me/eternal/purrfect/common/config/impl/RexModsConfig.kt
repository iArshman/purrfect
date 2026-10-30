package me.eternal.purrfect.common.config.impl

import me.eternal.purrfect.common.config.ConfigContainer
import me.eternal.purrfect.common.config.ConfigFlag

class RexModsConfig : ConfigContainer() {

    class SendSnapToAllConfig : ConfigContainer(hasGlobalState = true) {
        val triggerMode = unique("trigger_mode", "send_to_bar_only", "always_ask", "when_limit_reached") {
            customOptionTranslationPath = "features.options.send_snap_to_all_trigger_mode"
            addFlags(ConfigFlag.NO_DISABLE_KEY)
        }.apply { set("send_to_bar_only") }
        val limitThreshold = integer("limit_threshold", defaultValue = 200) {
            inputCheck = { it.toIntOrNull()?.coerceIn(1, 200) != null }
        }
        val batchSize = integer("batch_size", defaultValue = 200) {
            inputCheck = { it.toIntOrNull()?.coerceIn(1, 200) != null }
        }
        val batchDelayMs = integer("batch_delay_ms", defaultValue = 1000) {
            inputCheck = { it.toIntOrNull()?.coerceIn(0, 60000) != null }
        }
        val defaultTargetFilter = unique("default_target_filter", "all_friends", "streaks_only") {
            customOptionTranslationPath = "features.options.send_snap_to_all_target_filter"
            addFlags(ConfigFlag.NO_DISABLE_KEY)
        }.apply { set("all_friends") }
        val includeCameraSnaps = boolean("include_camera_snaps", defaultValue = true)
        val includeGalleryMedia = boolean("include_gallery_media", defaultValue = true)
    }

    val classicEmojiChatHeader = boolean("classic_emoji_chat_header", defaultValue = true)
    val sendToEmojiSelectBar = boolean("send_to_emoji_select_bar", defaultValue = true)
    val staticEmojiShortcutHeader = boolean("static_emoji_shortcut_header", defaultValue = true)
    val shortcutFriendsPerList = integer("shortcut_friends_per_list", defaultValue = 50) {
        inputCheck = { it.toIntOrNull()?.coerceIn(5, 200) != null }
    }
    val autoDeleteAfterViewingOnFriendAdd = boolean("auto_delete_after_viewing_on_friend_add", defaultValue = true)
    val sendSnapToAll = container("send_snap_to_all", SendSnapToAllConfig()).apply { globalState = true }
}
