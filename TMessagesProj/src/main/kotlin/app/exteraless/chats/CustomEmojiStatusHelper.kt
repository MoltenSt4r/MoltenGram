package app.exteraless.chats

import app.exteraless.OpenExteraConfig
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.DialogObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.AnimatedEmojiDrawable
import java.util.concurrent.ConcurrentHashMap

/**
 * Помощник для пользовательских эмодзи-статусов без Telegram Premium.
 *
 * Работает совместно с ProfileInvisibleMetadata (невидимые теги V3 в last_name)
 * и BotMetadataSyncHelper (синхронизация через служебного бота @BotFather).
 * Личное описание профиля (Bio) не изменяет.
 */
object CustomEmojiStatusHelper {

    const val KEY_PREFIX_USER = "custom_emoji_status_user_"
    private const val KEY_COLLECTIBLE_PREFIX = "custom_emoji_collectible_flag_"
    private const val KEY_SELF_COLLECTIBLE_PREFIX = "custom_emoji_self_collectible_"

    @JvmField
    val CHARS_32: List<Char> = buildList {
        for (i in 0 until 16) {
            add((0xFE00 + i).toChar())
        }
        add('\u200B')
        add('\u200C')
        add('\u200D')
        add('\u2060')
        add('\uFEFF')
        add('\u200E')
        add('\u200F')
        add('\u2061')
        add('\u2062')
        add('\u2063')
        add('\u2064')
        add('\u206A')
        add('\u206B')
        add('\u206C')
        add('\u206D')
        add('\u206E')
    }

    @JvmField
    val CHAR_MAP_32: Map<Char, Int> = CHARS_32.mapIndexed { index, c -> c to index }.toMap()

    const val START_MARKER_32 = "\u200D\u2060"
    const val END_MARKER_32 = "\u2060\u200D"

    private val FRAMED_TAG_REGEX = Regex(
        Regex.escape(START_MARKER_32) + "([\\uFE00-\\uFE0F\\u200B-\\u200D\\u2060\\uFEFF\\u200E\\u200F\\u2061-\\u2064\\u206A-\\u206E]+?)" + Regex.escape(END_MARKER_32)
    )
    private val LEGACY_VS_REGEX = Regex("[\\uFE00-\\uFE0F]{8,}")
    private val TG_EMOJI_REGEX = Regex("tg://emoji\\?id=(\\d+)")
    private val INVISIBLE_CLEANUP_REGEX = Regex("[\\u2060\\u200B-\\u200F\\uFEFF\\u2061-\\u2064\\u206A-\\u206E]{2,}")

    private val statusCache = ConcurrentHashMap<Long, Long>()
    private val collectibleCache = ConcurrentHashMap<Long, Boolean>()

    @JvmStatic
    fun isEnabled(): Boolean = OpenExteraConfig.customEmojiStatus()

    @JvmStatic
    fun encodeEmojiId(documentId: Long): String {
        if (documentId == 0L) return ""
        return ProfileInvisibleMetadata.encodeEmojiStatusTag(documentId, false)
    }

    @JvmStatic
    fun decodeEmojiId(text: String?): Long {
        if (text.isNullOrEmpty()) return 0L

        // Decode structured V2 / V1 metadata before generic Base-32 to prevent DELIMITER collisions
        ProfileInvisibleMetadata.decode(text)?.let { meta ->
            if (meta.emojiStatusDocId != 0L) {
                return meta.emojiStatusDocId
            }
        }

        // 1. Legacy Base-32 framed tag
        for (match in FRAMED_TAG_REGEX.findAll(text)) {
            val payload = match.groupValues[1]
            var value = 0L
            var valid = true
            for (ch in payload) {
                val idx = CHAR_MAP_32[ch]
                if (idx != null) {
                    value = value * 32L + idx
                } else {
                    valid = false
                    break
                }
            }
            if (valid && value != 0L) {
                return value
            }
        }

        // 2. Base-16 legacy variation selectors (length >= 8)
        val vsMatch = LEGACY_VS_REGEX.find(text)
        if (vsMatch != null) {
            try {
                val hexStr = buildString {
                    for (c in vsMatch.value) {
                        append((c.code - 0xFE00).toString(16))
                    }
                }
                val value = java.lang.Long.parseUnsignedLong(hexStr, 16)
                if (value != 0L) return value
            } catch (_: Exception) {}
        }

        // 3. tg://emoji?id=...
        val tgMatch = TG_EMOJI_REGEX.find(text)
        if (tgMatch != null) {
            try {
                val value = tgMatch.groupValues[1].toLong()
                if (value != 0L) return value
            } catch (_: Exception) {}
        }

        return 0L
    }

    @JvmStatic
    fun cleanBio(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        var cleaned = ProfileInvisibleMetadata.stripAllTags(text)
        cleaned = FRAMED_TAG_REGEX.replace(cleaned, "")
        cleaned = LEGACY_VS_REGEX.replace(cleaned, "")
        cleaned = TG_EMOJI_REGEX.replace(cleaned, "")
        cleaned = INVISIBLE_CLEANUP_REGEX.replace(cleaned, "")
        return cleaned.trim()
    }

    @JvmStatic
    fun cleanBioDisplay(text: String?): String {
        if (!isEnabled() || !OpenExteraConfig.cleanBioDisplay()) {
            return text ?: ""
        }
        return cleanBio(text)
    }

    @JvmStatic
    fun getEmojiStatusDocumentId(userId: Long): Long? {
        if (!isEnabled() || userId == 0L) return null
        val cached = statusCache[userId]
        if (cached != null) {
            return if (cached != 0L) cached else null
        }
        val prefId = OpenExteraConfig.getPreferences().getLong(KEY_PREFIX_USER + userId, 0L)
        if (prefId != 0L) {
            statusCache[userId] = prefId
            val isColl = OpenExteraConfig.getPreferences().getBoolean(KEY_COLLECTIBLE_PREFIX + userId, false)
            collectibleCache[userId] = isColl
            return prefId
        }
        return null
    }

    @JvmStatic
    fun isCollectibleStatus(userId: Long): Boolean {
        if (!isEnabled() || userId == 0L) return false
        collectibleCache[userId]?.let { return it }
        val isColl = OpenExteraConfig.getPreferences().getBoolean(KEY_COLLECTIBLE_PREFIX + userId, false)
        collectibleCache[userId] = isColl
        return isColl
    }

    @JvmStatic
    fun saveSelfCollectibleStatus(userId: Long, collectible: TLRPC.TL_emojiStatusCollectible?) {
        if (userId == 0L) return
        val editor = OpenExteraConfig.getPreferences().edit()
        val key = KEY_SELF_COLLECTIBLE_PREFIX + userId
        if (collectible == null) {
            editor.remove(key)
        } else {
            // Store pipe-delimited collectible metadata safely with escaped separators
            val safeTitle = (collectible.title ?: "").replace("\n", " ").replace("\u001F", " ")
            val safeSlug = (collectible.slug ?: "").replace("\n", " ").replace("\u001F", " ")
            val serialized = listOf(
                collectible.collectible_id.toString(),
                collectible.document_id.toString(),
                collectible.pattern_document_id.toString(),
                collectible.center_color.toString(),
                collectible.edge_color.toString(),
                collectible.pattern_color.toString(),
                collectible.text_color.toString(),
                collectible.until.toString(),
                collectible.flags.toString(),
                safeSlug,
                safeTitle
            ).joinToString("\u001F")
            editor.putString(key, serialized)
        }
        editor.apply()
    }

    @JvmStatic
    fun getSavedSelfCollectible(userId: Long, expectedDocId: Long): TLRPC.TL_emojiStatusCollectible? {
        if (userId == 0L || expectedDocId == 0L) return null
        val raw = OpenExteraConfig.getPreferences().getString(KEY_SELF_COLLECTIBLE_PREFIX + userId, null)
            ?: return null
        val parts = raw.split('\u001F')
        if (parts.size < 11) return null
        return try {
            val docId = parts[1].toLong()
            if (docId != expectedDocId) return null
            val status = TLRPC.TL_emojiStatusCollectible()
            status.collectible_id = parts[0].toLong()
            status.document_id = docId
            status.pattern_document_id = parts[2].toLong()
            status.center_color = parts[3].toInt()
            status.edge_color = parts[4].toInt()
            status.pattern_color = parts[5].toInt()
            status.text_color = parts[6].toInt()
            status.until = parts[7].toInt()
            status.flags = parts[8].toInt()
            status.slug = parts[9].takeIf { it.isNotEmpty() }
            status.title = parts[10].takeIf { it.isNotEmpty() }
            status
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    fun prefetchEmoji(account: Int, emojiId: Long) {
        if (emojiId == 0L) return
        try {
            AnimatedEmojiDrawable.getDocumentFetcher(account).fetchDocument(emojiId, null)
        } catch (_: Exception) {}
    }

    @JvmStatic
    fun setCustomEmojiStatus(account: Int, documentId: Long, callback: ((Boolean) -> Unit)? = null) {
        setCustomEmojiStatus(account, documentId, false, callback)
    }

    @JvmStatic
    fun setCustomEmojiStatus(
        account: Int,
        documentId: Long,
        isCollectible: Boolean,
        callback: ((Boolean) -> Unit)? = null
    ) {
        if (!isEnabled()) return
        val user = UserConfig.getInstance(account).currentUser ?: return
        if (user.premium) return
        statusCache[user.id] = documentId
        collectibleCache[user.id] = isCollectible
        OpenExteraConfig.getPreferences().edit()
            .putLong(KEY_PREFIX_USER + user.id, documentId)
            .putBoolean(KEY_COLLECTIBLE_PREFIX + user.id, isCollectible)
            .apply()
        prefetchEmoji(account, documentId)

        val currentDocId = DialogObject.getEmojiStatusDocumentId(user.emoji_status)
        val keepExistingCollectible =
            user.emoji_status is TLRPC.TL_emojiStatusCollectible && currentDocId == documentId
        if (!keepExistingCollectible) {
            val savedCollectible = if (isCollectible) getSavedSelfCollectible(user.id, documentId) else null
            if (savedCollectible != null) {
                user.emoji_status = savedCollectible
            } else {
                val status = TLRPC.TL_emojiStatus()
                status.document_id = documentId
                user.emoji_status = status
            }
        }
        user.flags2 = user.flags2 or 1
        MessagesController.getInstance(account).putUser(user, true)

        ProfileInvisibleMetadata.updateSelfEmojiStatus(account, documentId, isCollectible, callback)
    }

    @JvmStatic
    fun clearCustomEmojiStatus(account: Int, callback: ((Boolean) -> Unit)? = null) {
        if (!isEnabled()) return
        val user = UserConfig.getInstance(account).currentUser ?: return
        if (user.premium) return
        statusCache.remove(user.id)
        collectibleCache.remove(user.id)
        saveSelfCollectibleStatus(user.id, null)
        OpenExteraConfig.getPreferences().edit()
            .remove(KEY_PREFIX_USER + user.id)
            .remove(KEY_COLLECTIBLE_PREFIX + user.id)
            .apply()

        user.emoji_status = null
        user.flags2 = user.flags2 and 1.inv()
        MessagesController.getInstance(account).putUser(user, true)

        ProfileInvisibleMetadata.updateSelfEmojiStatus(account, 0L, false, callback)
    }
}
