package ee.carlrobert.codegpt.codecompletions.edit

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.thisLogger
import ee.carlrobert.codegpt.codecompletions.CodeCompletionEventListener
import ee.carlrobert.codegpt.ui.OverlayUtil
import ee.carlrobert.service.PartialCodeCompletionResponse
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.channels.ProducerScope
import okhttp3.Request
import okhttp3.sse.EventSource

class CodeCompletionStreamObserver(
    private val channel: ProducerScope<InlineCompletionElement>,
    private val eventListener: CodeCompletionEventListener,
) : StreamObserver<PartialCodeCompletionResponse> {

    companion object {
        private val logger = thisLogger()
    }

    private val messageBuilder = StringBuilder()
    private val emptyEventSource = object : EventSource {
        override fun cancel() {
        }

        override fun request(): Request {
            return Request.Builder().build()
        }
    }

    override fun onNext(value: PartialCodeCompletionResponse) {
        messageBuilder.append(value.partialCompletion)
        eventListener.onMessage(value.partialCompletion, emptyEventSource)
    }

    override fun onError(t: Throwable?) {
        logger.error("Error occurred while fetching code completion", t)
        if (t is StatusRuntimeException && t.status.code != Status.Code.UNAVAILABLE) {
            OverlayUtil.showNotification(
                t.message ?: "Something went wrong",
                NotificationType.ERROR
            )
        }
        channel.close(t)
    }

    override fun onCompleted() {
        eventListener.onComplete(messageBuilder)
    }
}