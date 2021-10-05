package tech.ula.utils

import android.content.Context
import android.util.Log
import io.sentry.Sentry
import io.sentry.android.AndroidSentryClientFactory
import io.sentry.event.BreadcrumbBuilder
import io.sentry.event.Event
import io.sentry.event.EventBuilder
import tech.ula.viewmodel.IllegalState

sealed class BreadcrumbType {
    // These types should override toString with return values of < 15 characters so that they
    // are easily identified in the Sentry UI.
    object ReceivedIntent : BreadcrumbType() {
        override fun toString(): String {
            return "Intent received"
        }
    }
    object SubmittedEvent : BreadcrumbType() {
        override fun toString(): String {
            return "Event submitted"
        }
    }
    object ReceivedEvent : BreadcrumbType() {
        override fun toString(): String {
            return "Event received"
        }
    }
    object ObservedState : BreadcrumbType() {
        override fun toString(): String {
            return "State observed"
        }
    }
    object RuntimeError : BreadcrumbType() {
        override fun toString(): String {
            return "Runtime error"
        }
    }
}

data class UlaBreadcrumb(
    val originatingClass: String,
    val type: BreadcrumbType,
    val details: String
)

interface Logger {
    fun initialize(context: Context? = null)

    fun addBreadcrumb(breadcrumb: UlaBreadcrumb)

    fun addExceptionBreadcrumb(err: Exception)

    fun sendIllegalStateLog(state: IllegalState)

    fun sendEvent(message: String)
}

class SentryLogger : Logger {
    override fun initialize(context: Context?) {
        Sentry.init(AndroidSentryClientFactory(context!!))
    }

    override fun addBreadcrumb(breadcrumb: UlaBreadcrumb) {
        val key = "${breadcrumb.type}"
        val value = "${breadcrumb.originatingClass}: ${breadcrumb.details}"
        val sentryBreadcrumb = BreadcrumbBuilder()
                .setCategory(key)
                .setMessage(value)
                .build()
        Sentry.getContext().recordBreadcrumb(sentryBreadcrumb)
        Log.i("Breadcrumb", "$key $value")
    }

    override fun addExceptionBreadcrumb(err: Exception) {
        val stackTrace = err.stackTrace.first()
        val breadcrumb = BreadcrumbBuilder()
                .setCategory("Exception")
                .setData(mapOf(
                        "type" to err.javaClass.simpleName,
                        "file" to stackTrace.fileName,
                        "lineNumber" to stackTrace.lineNumber.toString()
                ))
                .build()
        Sentry.getContext().recordBreadcrumb(breadcrumb)
        //Log.e("ExceptionBreadcrumb", err.stackTrace.message)
    }

    override fun sendIllegalStateLog(state: IllegalState) {
        val message = state.javaClass.simpleName
        val event = EventBuilder()
                .withMessage(message)
                .withLevel(Event.Level.ERROR)
        Sentry.capture(event)
        Log.e("ILLEGAL_STATE", message)
    }

    override fun sendEvent(message: String) {
        val event = EventBuilder()
                .withMessage(message)
                .withLevel(Event.Level.ERROR)
        Sentry.capture(event)
        Log.e("EVENT", message)
    }
}