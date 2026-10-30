package me.eternal.purrfect.core.wrapper.impl

import me.eternal.purrfect.common.data.MessageUpdate
import me.eternal.purrfect.core.ModContext
import me.eternal.purrfect.core.util.CallbackBuilder
import me.eternal.purrfect.core.util.dataBuilder
import me.eternal.purrfect.core.util.ktx.getObjectField
import me.eternal.purrfect.core.util.ktx.setObjectField
import me.eternal.purrfect.core.wrapper.AbstractWrapper
import me.eternal.purrfect.mapper.impl.CallbackMapper

typealias CallbackResult = (error: String?) -> Unit

class ConversationManager(
    val context: ModContext,
    obj: Any
) : AbstractWrapper(obj) {
    private fun findMethodByName(name: String) = sequence {
        var current: Class<*>? = context.classCache.conversationManager
        while (current != null && current != Any::class.java && current != Object::class.java) {
            yield(current)
            current = current.superclass
        }
    }.flatMap { clazz -> clazz.declaredMethods.asSequence() }
        .firstOrNull { it.name == name }
        ?: throw RuntimeException("Could not find method $name")

    private val updateMessageMethod by lazy { findMethodByName("updateMessage") }
    private val fetchConversationWithMessagesPaginatedMethod by lazy { findMethodByName("fetchConversationWithMessagesPaginated") }
    private val fetchConversationWithMessagesMethod by lazy { findMethodByName("fetchConversationWithMessages") }
    private val fetchMessageByServerId by lazy { findMethodByName("fetchMessageByServerId") }
    private val fetchMessagesByServerIds by lazy { findMethodByName("fetchMessagesByServerIds") }
    private val fetchPrefetchableMessagesForConversationsMethod by lazy { findMethodByName("fetchPrefetchableMessagesForConversations") }
    private val displayedMessagesMethod by lazy { findMethodByName("displayedMessages") }
    private val fetchMessage by lazy { findMethodByName("fetchMessage") }
    private val clearConversation by lazy { findMethodByName("clearConversation") }
    private val getOneOnOneConversationIds by lazy { findMethodByName("getOneOnOneConversationIds") }
    private val dismissStreakRestore by lazy { findMethodByName("dismissStreakRestore") }
    private val reactToMessageMethod by lazy { findMethodByName("reactToMessage") }


    private fun getCallbackClass(name: String): Class<*> {
        lateinit var result: Class<*>
        context.mappings.useMapper(CallbackMapper::class) {
            result = context.androidContext.classLoader.loadClass(callbacks.get()!![name])
        }
        return result
    }


    fun updateMessage(conversationId: String, messageId: Long, action: MessageUpdate, onResult: CallbackResult = {}) {
        updateMessageMethod.invoke(
            instanceNonNull(),
            SnapUUID(conversationId).instanceNonNull(),
            messageId,
            context.classCache.messageUpdateEnum.enumConstants!!.first { it.toString() == action.toString() },
            CallbackBuilder(getCallbackClass("Callback"))
                .override("onSuccess") { onResult(null) }
                .override("onError") { onResult(it.arg<Any>(0).toString()) }.build()
        )
    }

    fun fetchConversationWithMessagesPaginated(conversationId: String, lastMessageId: Long, amount: Int, onSuccess: (message: List<Message>) -> Unit, onError: (error: String) -> Unit) {
        val callback = CallbackBuilder(getCallbackClass("FetchConversationWithMessagesCallback"))
            .override("onFetchConversationWithMessagesComplete") { param ->
                onSuccess(param.arg<List<*>>(1).map { Message(it) })
            }
            .override("onServerRequest", shouldUnhook = false) {}
            .override("onError") {
                onError(it.arg<Any>(0).toString())
            }.build()
        fetchConversationWithMessagesPaginatedMethod.invoke(instanceNonNull(), conversationId.toSnapUUID().instanceNonNull(), lastMessageId, amount, callback)
    }

    fun fetchConversationWithMessages(conversationId: String, onSuccess: (List<Message>) -> Unit, onError: (error: String) -> Unit) {
        fetchConversationWithMessagesMethod.invoke(
            instanceNonNull(),
            conversationId.toSnapUUID().instanceNonNull(),
            CallbackBuilder(getCallbackClass("FetchConversationWithMessagesCallback"))
                .override("onFetchConversationWithMessagesComplete") { param ->
                    onSuccess(param.arg<List<*>>(1).map { Message(it) })
                }
                .override("onServerRequest", shouldUnhook = false) {}
                .override("onError") {
                    onError(it.arg<Any>(0).toString())
                }.build()
        )
    }

    fun displayedMessages(conversationId: String, messageId: Long, onResult: CallbackResult = {}) {
        displayedMessagesMethod.invoke(
            instanceNonNull(),
            conversationId.toSnapUUID().instanceNonNull(),
            messageId,
            CallbackBuilder(getCallbackClass("Callback"))
                .override("onSuccess") { onResult(null) }
                .override("onError") { onResult(it.arg<Any>(0).toString()) }.build()
        )
    }

    fun fetchMessage(conversationId: String, messageId: Long, onSuccess: (Message) -> Unit, onError: (error: String) -> Unit = {}) {
        fetchMessage.invoke(
            instanceNonNull(),
            conversationId.toSnapUUID().instanceNonNull(),
            messageId,
            CallbackBuilder(getCallbackClass("FetchMessageCallback"))
                .override("onFetchMessageComplete") { param ->
                    onSuccess(Message(param.arg(0)))
                }
                .override("onError") {
                    onError(it.arg<Any>(0).toString())
                }.build()
        )
    }

    fun fetchMessageByServerId(conversationId: String, serverMessageId: Long, onSuccess: (Message) -> Unit, onError: (error: String) -> Unit) {
        val serverMessageIdentifier = context.classCache.serverMessageIdentifier.dataBuilder {
            set("mServerConversationId",  conversationId.toSnapUUID().instanceNonNull())
            set("mServerMessageId", serverMessageId)
        }
        val conversationUuid = conversationId.toSnapUUID().instanceNonNull()

        val callback = CallbackBuilder(getCallbackClass("FetchMessageCallback"))
            .override("onFetchMessageComplete") { param ->
                onSuccess(Message(param.arg(0)))
            }
            .override("onError") {
                onError(it.arg<Any>(0).toString())
            }.build()

        val args = fetchMessageByServerId.parameterTypes.mapIndexed { index, parameterType ->
            when {
                parameterType.isInstance(serverMessageIdentifier) -> serverMessageIdentifier
                parameterType.isInstance(callback) -> callback
                parameterType.isInstance(conversationUuid) -> conversationUuid
                parameterType == Boolean::class.javaPrimitiveType || parameterType == Boolean::class.javaObjectType -> false
                else -> throw IllegalStateException(
                    "Unsupported fetchMessageByServerId parameter at index $index: ${parameterType.name}"
                )
            }
        }.toTypedArray()

        fetchMessageByServerId.invoke(instanceNonNull(), *args)
    }

    fun fetchMessagesByServerIds(conversationId: String, serverMessageIds: List<Long>, onSuccess: (List<Message>) -> Unit, onError: (error: String) -> Unit) {
        fetchMessagesByServerIds.invoke(
            instanceNonNull(),
            serverMessageIds.map {
                CallbackBuilder.createEmptyObject(context.classCache.serverMessageIdentifier.constructors.first())?.apply {
                    setObjectField("mServerConversationId", conversationId.toSnapUUID().instanceNonNull())
                    setObjectField("mServerMessageId", it)
                }
            },
            CallbackBuilder(getCallbackClass("FetchMessagesByServerIdsCallback"))
                .override("onSuccess") { param ->
                    onSuccess(param.arg<List<*>>(0).mapNotNull {
                        Message(it?.getObjectField("mMessage") ?: return@mapNotNull null)
                    })
                }
                .override("onError") {
                    onError(it.arg<Any>(0).toString())
                }.build()
        )
    }

    fun fetchPrefetchableMessagesForConversations(
        conversationIds: List<String>,
        strategyName: String,
        messagesPerConversation: Int,
        onSuccess: (List<Message>) -> Unit = {},
        onError: (error: String) -> Unit = {}
    ) {
        val prefetchRequestClass = fetchPrefetchableMessagesForConversationsMethod.parameterTypes.firstOrNull {
            it.name == "com.snapchat.client.messaging.PrefetchRequest"
        } ?: error("PrefetchRequest parameter type not found")
        val strategyClass = context.androidContext.classLoader.loadClass("com.snapchat.client.messaging.PrefetchStrategy")
        val strategy = strategyClass.enumConstants?.firstOrNull { it.toString() == strategyName }
            ?: error("PrefetchStrategy $strategyName not found")
        val prefetchRequest = prefetchRequestClass
            .getConstructor(strategyClass, Int::class.javaPrimitiveType)
            .newInstance(strategy, messagesPerConversation)

        fetchPrefetchableMessagesForConversationsMethod.invoke(
            instanceNonNull(),
            conversationIds.map { it.toSnapUUID().instanceNonNull() }.toCollection(ArrayList()),
            prefetchRequest,
            CallbackBuilder(getCallbackClass("FetchMessagesCallback"))
                .override("onFetchMessagesComplete") { param ->
                    onSuccess(param.arg<List<*>>(0).map { Message(it) })
                }
                .override("onError") {
                    onError(it.arg<Any>(0).toString())
                }.build()
        )
    }

    fun clearConversation(conversationId: String, onSuccess: () -> Unit, onError: (error: String) -> Unit) {
        val callback = CallbackBuilder(getCallbackClass("Callback"))
            .override("onSuccess") { onSuccess() }
            .override("onError") { onError(it.arg<Any>(0).toString()) }.build()
        clearConversation.invoke(instanceNonNull(), conversationId.toSnapUUID().instanceNonNull(), callback)
    }

    fun getOneOnOneConversationIds(userIds: List<String>, onSuccess: (List<Pair<String, String>>) -> Unit, onError: (error: String) -> Unit) {
        val callback = CallbackBuilder(getCallbackClass("GetOneOnOneConversationIdsCallback"))
            .override("onSuccess") { param ->
                onSuccess(param.arg<ArrayList<*>>(0).map {
                    SnapUUID(it.getObjectField("mUserId")).toString() to SnapUUID(it.getObjectField("mConversationId")).toString()
                })
            }
            .override("onError") { onError(it.arg<Any>(0).toString()) }.build()
        getOneOnOneConversationIds.invoke(instanceNonNull(), userIds.map { it.toSnapUUID().instanceNonNull() }.toMutableList(), callback)
    }

    fun editMessage(conversationId: String, messageId: Long, content: ByteArray, onSuccess: () -> Unit, onError: (error: String) -> Unit) {
        val editMessageMethod = instanceNonNull()::class.java.methods.first { it.name == "editMessage" }
        editMessageMethod.invoke(instanceNonNull(), editMessageMethod.parameterTypes[0].dataBuilder {
            set("mConversationId", conversationId.toSnapUUID().instanceNonNull())
            set("mMessageId", messageId)
        }, editMessageMethod.parameterTypes[1].dataBuilder {
            set("mContent", content)
            set("mMentionInfo", null)
        }, CallbackBuilder(getCallbackClass("Callback"))
            .override("onSuccess") { onSuccess() }
            .override("onError") { onError(it.arg<Any>(0).toString()) }.build()
        )
    }

    fun isEditMessageSupported() = instanceNonNull()::class.java.methods.any { it.name == "editMessage" }

    fun dismissStreakRestore(conversationId: String, onSuccess: () -> Unit, onError: (error: String) -> Unit) {
        val callback = CallbackBuilder(getCallbackClass("Callback"))
            .override("onSuccess") { onSuccess() }
            .override("onError") { onError(it.arg<Any>(0).toString()) }.build()
        dismissStreakRestore.invoke(instanceNonNull(), conversationId.toSnapUUID().instanceNonNull(), callback)
    }

    fun reactToMessage(conversationId: String, messageId: Long, emoji: String? = null, intentionType: Long? = null, onSuccess: () -> Unit, onError: (error: String) -> Unit) {
        reactToMessageMethod.invoke(
            instanceNonNull(),
            conversationId.toSnapUUID().instanceNonNull(),
            messageId,
            reactToMessageMethod.parameterTypes[2].dataBuilder {
                set("mEmoji", emoji)
                set("mIntentionType", intentionType)
            },
            reactToMessageMethod.parameterTypes[3].dataBuilder {
                set("mMetricsMessageMediaType", "NO_MEDIA")
                set("mMetricsMessageType", "TEXT")
                set("mReactionSource", "NONE")
            },
            CallbackBuilder(getCallbackClass("Callback"))
            .override("onSuccess") { onSuccess() }
            .override("onError") { onError(it.arg<Any>(0).toString()) }.build()
        )
    }

    fun updateConversationRetentionPolicy(
        conversationId: String,
        deleteAfterViewing: Boolean = true,
        onSuccess: () -> Unit = {},
        onError: (error: String) -> Unit = {}
    ) {
        val cm = instanceNonNull()
        val convUuid = conversationId.toSnapUUID().instanceNonNull()

        val allMethods = sequence {
            var current: Class<*>? = cm.javaClass
            while (current != null && current != Any::class.java && current != Object::class.java) {
                yieldAll(current.declaredMethods.asSequence())
                current = current.superclass
            }
        }.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .distinctBy { "${it.name}(${it.parameterTypes.joinToString(",") { p -> p.name }})" }
            .toList()

        val candidateMethods = allMethods.filter { m ->
            val n = m.name.lowercase()
            val paramNames = m.parameterTypes.joinToString(",") { it.name.lowercase() }
            (n.contains("retention") ||
                n.contains("viewingpolicy") ||
                n.contains("postopen") ||
                paramNames.contains("retention") ||
                paramNames.contains("viewingpolicy")) &&
                m.parameterTypes.any { it.name.contains("UUID") } &&
                m.parameterTypes.lastOrNull()?.name?.contains("Callback") == true
        }.ifEmpty {
            allMethods.filter { m ->
                m.parameterCount >= 3 &&
                    m.parameterTypes[0].name.contains("UUID") &&
                    m.parameterTypes.last().name.endsWith("Callback") &&
                    m.parameterTypes.any { it.isEnum && (it.name.contains("Retention", ignoreCase = true) || it.name.contains("Policy", ignoreCase = true) || it.name.contains("Mode", ignoreCase = true)) }
            }
        }

        if (candidateMethods.isEmpty()) {
            val available = allMethods
                .filter { it.parameterTypes.firstOrNull()?.name?.contains("UUID") == true }
                .joinToString("; ") { "${it.name}(${it.parameterTypes.joinToString(",") { p -> p.simpleName }})" }
            context.log.warn("[RexMods] No retention method found on ConversationManager. UUID methods: $available", "RexMods")
            onError("No retention method found on ConversationManager")
            return
        }

        fun selectEnumConstant(enumType: Class<*>, immediate: Boolean): Any? {
            val constants = enumType.enumConstants as? Array<out Enum<*>> ?: return null
            if (constants.isEmpty()) return null
            val typeName = enumType.simpleName.lowercase()
            return if (immediate) {
                constants.firstOrNull { c ->
                    val name = c.name.uppercase()
                    name.contains("IMMEDIAT") ||
                        name.contains("AFTER_VIEW") ||
                        name.contains("DELETE") ||
                        name.contains("ENVELOPE") ||
                        name.contains("ZERO") ||
                        name.contains("READ") ||
                        (typeName.contains("postopen") && name == "ENVELOPE")
                } ?: constants.firstOrNull { c ->
                    !c.name.uppercase().contains("24") &&
                        !c.name.uppercase().contains("TWENTY") &&
                        !c.name.uppercase().contains("HOUR") &&
                        !c.name.uppercase().contains("INFINITE")
                } ?: constants.firstOrNull()
            } else {
                constants.firstOrNull { c ->
                    val name = c.name.uppercase()
                    name.contains("24") || name.contains("TWENTY") || name.contains("HOUR") || name.contains("DAY")
                } ?: constants.lastOrNull()
            }
        }

        fun buildPolicyParameter(paramType: Class<*>, immediate: Boolean): Any? {
            return when {
                paramType.isInstance(convUuid) -> convUuid
                paramType.isEnum -> selectEnumConstant(paramType, immediate)
                paramType == Boolean::class.javaPrimitiveType || paramType == Boolean::class.javaObjectType -> {
                    // In Djinni methods taking a boolean alongside retention, false = not infinite / immediate
                    false
                }
                paramType == Long::class.javaPrimitiveType || paramType == Long::class.javaObjectType -> {
                    if (immediate) 0L else 86400L
                }
                paramType == Int::class.javaPrimitiveType || paramType == Int::class.javaObjectType -> {
                    if (immediate) 0 else 86400
                }
                else -> {
                    // Construct Djinni data class (e.g., ConversationRetentionPolicy)
                    runCatching {
                        val ctor = paramType.declaredConstructors.firstOrNull() ?: return@runCatching null
                        ctor.isAccessible = true
                        val instance = CallbackBuilder.createEmptyObject(ctor) ?: return@runCatching null
                        paramType.declaredFields.forEach { field ->
                            field.isAccessible = true
                            val fName = field.name.lowercase()
                            val fType = field.type
                            when {
                                fType.isEnum -> field.set(instance, selectEnumConstant(fType, immediate))
                                fType == Boolean::class.javaPrimitiveType || fType == Boolean::class.javaObjectType -> {
                                    val value = when {
                                        fName.contains("infinite") -> false
                                        fName.contains("send") -> true
                                        else -> immediate
                                    }
                                    field.set(instance, value)
                                }
                                fType == Long::class.javaPrimitiveType || fType == Long::class.javaObjectType -> {
                                    field.set(instance, if (immediate) 0L else 86400L)
                                }
                                fType == Int::class.javaPrimitiveType || fType == Int::class.javaObjectType -> {
                                    field.set(instance, if (immediate) 0 else 86400)
                                }
                            }
                        }
                        instance
                    }.getOrNull()
                }
            }
        }

        var invokedCount = 0
        var succeeded = false
        var lastError: String? = null

        candidateMethods.forEach { method ->
            runCatching {
                method.isAccessible = true
                val callbackParamType = method.parameterTypes.last()
                val callbackClass = runCatching { getCallbackClass("Callback") }.getOrDefault(callbackParamType)
                val callback = CallbackBuilder(callbackClass)
                    .override("onSuccess") {
                        if (!succeeded) {
                            succeeded = true
                            context.log.info("[RexMods] Retention policy set to Delete After Viewing via ${method.name} for $conversationId", "RexMods")
                            onSuccess()
                        }
                    }
                    .override("onError") {
                        val err = it.arg<Any>(0).toString()
                        lastError = err
                        context.log.warn("[RexMods] ${method.name} reported error for $conversationId: $err", "RexMods")
                    }
                    .build()

                val args = method.parameterTypes.mapIndexed { idx, pType ->
                    if (idx == method.parameterCount - 1) {
                        callback
                    } else {
                        buildPolicyParameter(pType, deleteAfterViewing)
                    }
                }.toTypedArray()

                if (args.any { it == null }) {
                    context.log.warn("[RexMods] Skipping ${method.name}: could not build all arguments (${args.map { it?.javaClass?.simpleName }})", "RexMods")
                    return@runCatching
                }

                invokedCount++
                context.log.info("[RexMods] Invoking ${method.name}(${args.dropLast(1).joinToString(", ")}) for conv=$conversationId", "RexMods")
                method.invoke(cm, *args)
            }.onFailure { err ->
                lastError = err.message ?: err.toString()
                context.log.warn("[RexMods] Exception invoking ${method.name}: ${err.message}", "RexMods")
            }
        }

        if (invokedCount == 0 && !succeeded) {
            onError(lastError ?: "Could not invoke any retention policy method")
        }
    }
}
