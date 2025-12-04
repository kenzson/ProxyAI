package ee.carlrobert.codegpt.codecompletions

import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.openapi.components.service
import ee.carlrobert.codegpt.psistructure.PsiStructureProvider
import ee.carlrobert.codegpt.settings.configuration.ConfigurationSettings
import ee.carlrobert.codegpt.util.GitUtil

object InfillRequestUtil {

    suspend fun buildInfillRequest(request: InlineCompletionRequest): InfillRequest {
        val infillRequestBuilder = InfillRequest.Builder(request.editor)

        val project = request.editor.project ?: return infillRequestBuilder.build()
        if (service<ConfigurationSettings>().state.codeCompletionSettings.gitDiffEnabled) {
            GitUtil.getCurrentChanges(project)?.let { diff ->
                if (diff.isNotEmpty()) {
                    infillRequestBuilder.gitDiff(diff)
                }
            }
        }

        if (service<ConfigurationSettings>().state.codeCompletionSettings.collectDependencyStructure) {
            val depth =
                service<ConfigurationSettings>().state.codeCompletionSettings.psiStructureAnalyzeDepth
            val psiStructure = PsiStructureProvider().get(listOf(request.file), depth)
            if (psiStructure.isNotEmpty()) {
                infillRequestBuilder.addDependenciesStructure(psiStructure)
            }
        }

        return infillRequestBuilder.build()
    }
}