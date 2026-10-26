package app.exteraless.chats

import app.exteraless.OpenExteraConfig
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_account
import org.telegram.ui.Components.AnimatedEmojiDrawable
import java.util.concurrent.ConcurrentHashMap

/**
 * Помощник для пользовательских эмодзи-статусов без Telegram Premium.
 *
 * Кодирует и декодирует ID эмодзи в невидимые Unicode-символы Base-32 в описании профиля (Bio).
 * Предоставляет методы для чтения, установки, удаления и отображения статусов.
 * Строго изолирует статус по каждому аккаунту и очищает любые предыдущие теги.
 */
object CustomEmojiStatusHelper {

    const val KEY_PREFIX_USER = "custom_emoji_status_user_"

    @JvmField
    val CHARS_32: List<Char> = buildList {
        // 16 Unicode Variation Selectors (VS1 - VS16: U+FE00 .. U+FE0F)
        for (i in 0 until 16) {
            add((0xFE00 + i).toChar())
        }
        // 5 Zero-Width characters
        add('\u200B')
        add('\u200C')
        add('\u200D')
        add('\u2060')
        add('\uFEFF')
        // 2 Directional formatting marks
        add('\u200E')
        add('\u200F')
        // 4 Invisible math operators
        add('\u2061')
        add('\u2062')
        add('\u2063')
        add('\u2064')
        // 5 Invisible control characters
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

    @JvmStatic
    fun isEnabled(): Boolean = OpenExteraConfig.customEmojiStatus()

    @JvmStatic
    fun encodeEmojiId(documentId: Long): String {
        if (documentId <= 0L) return ""
        val digits = StringBuilder()
        var temp = documentId
        while (temp > 0L) {
            digits.append(CHARS_32[(temp % 32L).toInt()])
            temp /= 32L
        }
        return START_MARKER_32 + digits.reverse().toString() + END_MARKER_32
    }

    @JvmStatic
    fun decodeEmojiId(text: String?): Long {
        if (text.isNullOrEmpty()) return 0L

        // 1. Base-32 framed tag
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
            if (valid && value > 0L) {
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
                if (value > 0L) return value
            } catch (_: Exception) {}
        }

        // 3. tg://emoji?id=...
        val tgMatch = TG_EMOJI_REGEX.find(text)
        if (tgMatch != null) {
            try {
                val value = tgMatch.groupValues[1].toLong()
                if (value > 0L) return value
            } catch (_: Exception) {}
        }

        return 0L
    }

    @JvmStatic
    fun cleanBio(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        var cleaned = FRAMED_TAG_REGEX.replace(text, "")
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
    fun formatBioWithTag(baseBio: String?, tag: String, isPremium: Boolean): String {
        val clean = cleanBio(baseBio)
        if (tag.isEmpty()) return clean
        val maxLimit = if (isPremium) 140 else 70
        var allowedBio = clean
        if (allowedBio.length + tag.length + 1 > maxLimit) {
            val maxLen = (maxLimit - tag.length - 1).coerceAtLeast(0)
            allowedBio = allowedBio.take(maxLen).trim()
        }
        return if (allowedBio.isEmpty()) tag else "$allowedBio $tag"
    }

    @JvmStatic
    fun getEmojiStatusDocumentId(userId: Long): Long? {
        if (!isEnabled() || userId == 0L) return null
        val cached = statusCache[userId]
        if (cached != null) {
            return if (cached > 0L) cached else null
        }
        val prefId = OpenExteraConfig.getPreferences().getLong(KEY_PREFIX_USER + userId, 0L)
        if (prefId > 0L) {
            statusCache[userId] = prefId
            return prefId
        }
        return null
    }

    @JvmStatic
    fun processUserBio(account: Int, userId: Long, bio: String?) {
        if (!isEnabled() || userId == 0L) return
        val docId = decodeEmojiId(bio)
        val prevDocId = statusCache[userId] ?: OpenExteraConfig.getPreferences().getLong(KEY_PREFIX_USER + userId, 0L)
        if (docId > 0L) {
            statusCache[userId] = docId
            OpenExteraConfig.getPreferences().edit().putLong(KEY_PREFIX_USER + userId, docId).apply()
            prefetchEmoji(account, docId)
        } else if (prevDocId > 0L && bio != null) {
            statusCache.remove(userId)
            OpenExteraConfig.getPreferences().edit().remove(KEY_PREFIX_USER + userId).apply()
        }
        if (docId != prevDocId) {
            AndroidUtilities.runOnUIThread {
                val user = MessagesController.getInstance(account).getUser(userId)
                if (user != null) {
                    NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.userEmojiStatusUpdated, user)
                }
                NotificationCenter.getInstance(account).postNotificationName(
                    NotificationCenter.updateInterfaces,
                    MessagesController.UPDATE_MASK_EMOJI_STATUS or MessagesController.UPDATE_MASK_NAME
                )
            }
        }
    }

    @JvmStatic
    fun prefetchEmoji(account: Int, emojiId: Long) {
        if (emojiId <= 0L) return
        try {
            AnimatedEmojiDrawable.getDocumentFetcher(account).fetchDocument(emojiId, null)
        } catch (_: Exception) {}
    }

    @JvmStatic
    fun getTagForCurrentUser(account: Int): String {
        if (!isEnabled()) return ""
        val user = UserConfig.getInstance(account).currentUser ?: return ""
        val docId = getEmojiStatusDocumentId(user.id) ?: 0L
        return if (docId > 0L) encodeEmojiId(docId) else ""
    }

    @JvmStatic
    fun setCustomEmojiStatus(account: Int, documentId: Long, callback: ((Boolean) -> Unit)? = null) {
        if (!isEnabled()) return
        val user = UserConfig.getInstance(account).currentUser ?: return
        if (user.premium) return
        statusCache[user.id] = documentId
        OpenExteraConfig.getPreferences().edit()
            .putLong(KEY_PREFIX_USER + user.id, documentId)
            .apply()
        prefetchEmoji(account, documentId)

        val status = TLRPC.TL_emojiStatus()
        status.document_id = documentId
        user.emoji_status = status
        user.flags2 = user.flags2 or 1
        MessagesController.getInstance(account).putUser(user, true)

        val userFull = MessagesController.getInstance(account).getUserFull(user.id)
        val currentBio = userFull?.about ?: ""
        val tag = encodeEmojiId(documentId)
        val newBio = formatBioWithTag(currentBio, tag, false)

        val req = TL_account.updateProfile()
        req.about = newBio
        req.flags = 4

        ConnectionsManager.getInstance(account).sendRequest(req) { _, error ->
            val success = error == null
            if (success && userFull != null) {
                userFull.about = newBio
            }
            AndroidUtilities.runOnUIThread {
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.userEmojiStatusUpdated, user)
                NotificationCenter.getInstance(account).postNotificationName(
                    NotificationCenter.updateInterfaces,
                    MessagesController.UPDATE_MASK_EMOJI_STATUS or MessagesController.UPDATE_MASK_NAME
                )
                callback?.invoke(success)
            }
        }
    }

    @JvmStatic
    fun clearCustomEmojiStatus(account: Int, callback: ((Boolean) -> Unit)? = null) {
        if (!isEnabled()) return
        val user = UserConfig.getInstance(account).currentUser ?: return
        if (user.premium) return
        statusCache.remove(user.id)
        OpenExteraConfig.getPreferences().edit()
            .remove(KEY_PREFIX_USER + user.id)
            .apply()

        user.emoji_status = null
        user.flags2 = user.flags2 and 1.inv()
        MessagesController.getInstance(account).putUser(user, true)

        val userFull = MessagesController.getInstance(account).getUserFull(user.id)
        val currentBio = userFull?.about ?: ""
        val cleanedBio = cleanBio(currentBio)

        val req = TL_account.updateProfile()
        req.about = cleanedBio
        req.flags = 4

        ConnectionsManager.getInstance(account).sendRequest(req) { _, error ->
            val success = error == null
            if (success && userFull != null) {
                userFull.about = cleanedBio
            }
            AndroidUtilities.runOnUIThread {
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.userEmojiStatusUpdated, user)
                NotificationCenter.getInstance(account).postNotificationName(
                    NotificationCenter.updateInterfaces,
                    MessagesController.UPDATE_MASK_EMOJI_STATUS or MessagesController.UPDATE_MASK_NAME
                )
                callback?.invoke(success)
            }
        }
    }
}
